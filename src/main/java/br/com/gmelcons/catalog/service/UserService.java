package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.dto.Dtos.UserCreateRequest;
import br.com.gmelcons.catalog.dto.Dtos.UserDto;
import br.com.gmelcons.catalog.dto.Dtos.UserUpdateRequest;
import br.com.gmelcons.catalog.entity.AppUser;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static br.com.gmelcons.catalog.service.AuditService.fields;

/** Gestão de usuários: somente ADMIN. A senha nunca aparece em DTOs nem na auditoria. */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserService::toDto).toList();
    }

    @Transactional
    public UserDto create(UserCreateRequest req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw BusinessException.conflict("Já existe um usuário com este login");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(req.role());
        user.setEnabled(true);
        users.save(user);
        audit.log(AuditOperation.USER_CREATE, "AppUser", user.getId(), null, snapshot(user));
        return toDto(user);
    }

    @Transactional
    public UserDto update(Long id, UserUpdateRequest req) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        boolean self = user.getUsername().equals(currentUser.username());
        if (self && (!req.enabled() || req.role() != user.getRole())) {
            throw BusinessException.conflict("Você não pode desativar nem alterar o papel da sua própria conta");
        }
        Map<String, Object> before = snapshot(user);
        user.setFullName(req.fullName().trim());
        user.setRole(req.role());
        user.setEnabled(req.enabled());
        boolean passwordChanged = req.password() != null && !req.password().isBlank();
        if (passwordChanged) {
            user.setPasswordHash(encoder.encode(req.password()));
        }
        Map<String, Object> after = snapshot(user);
        after.put("passwordChanged", passwordChanged);
        audit.log(AuditOperation.USER_UPDATE, "AppUser", user.getId(), before, after);
        return toDto(user);
    }

    private static Map<String, Object> snapshot(AppUser u) {
        return fields("username", u.getUsername(), "fullName", u.getFullName(), "role", u.getRole(),
                "enabled", u.isEnabled());
    }

    private static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
