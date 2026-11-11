package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Bloqueio temporário de conta após falhas seguidas de login (mitiga tentativa de adivinhar senha).
 * Usuário inexistente é ignorado em silêncio: nada no resultado revela se a conta existe.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AppUserRepository users;
    private final AuditService audit;

    @Value("${app.security.max-failed-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lock-minutes:15}")
    private long lockMinutes;

    @Transactional
    public void recordFailure(String rawUsername, String ipAddress) {
        users.findByUsername(normalize(rawUsername)).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= maxAttempts) {
                Instant until = Instant.now().plus(Duration.ofMinutes(lockMinutes));
                user.setLockedUntil(until);
                user.setFailedLoginAttempts(0);
                audit.logAsFrom(user.getUsername(), ipAddress, AuditOperation.ACCOUNT_LOCKED, "AppUser", user.getId(), null,
                        AuditService.fields("lockedUntil", until.toString(), "minutes", lockMinutes));
            } else {
                user.setFailedLoginAttempts(attempts);
            }
        });
    }

    @Transactional
    public void recordSuccess(String rawUsername) {
        users.findByUsername(normalize(rawUsername)).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        });
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
