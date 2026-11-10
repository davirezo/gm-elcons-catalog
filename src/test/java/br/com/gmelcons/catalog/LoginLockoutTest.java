package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.Role;
import br.com.gmelcons.catalog.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Bloqueio de conta por tentativas de login")
class LoginLockoutTest extends IntegrationTestBase {

    private static final String PASSWORD = "Senha@12345";

    private void login(String username, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/login").param("username", username).param("password", password).with(csrf()))
                .andExpect(status().is(expectedStatus));
    }

    private AppUser reload(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    @Test
    @DisplayName("5 senhas erradas bloqueiam a conta e a senha certa deixa de funcionar")
    void lockedAfterFiveFailures() throws Exception {
        String username = "lock-" + uid();
        ensureUser(username, Role.OPERADOR, PASSWORD, true);

        for (int i = 0; i < 5; i++) {
            login(username, "errada", 401);
        }
        login(username, PASSWORD, 401);

        assertThat(reload(username).getLockedUntil()).isAfter(Instant.now());
        assertThat(hasAudit(AuditOperation.ACCOUNT_LOCKED, username)).isTrue();
    }

    @Test
    @DisplayName("menos de 5 falhas não bloqueiam e um login correto zera o contador")
    void successResetsCounter() throws Exception {
        String username = "reset-" + uid();
        ensureUser(username, Role.OPERADOR, PASSWORD, true);

        for (int i = 0; i < 4; i++) {
            login(username, "errada", 401);
        }
        assertThat(reload(username).getFailedLoginAttempts()).isEqualTo(4);

        login(username, PASSWORD, 200);

        assertThat(reload(username).getFailedLoginAttempts()).isZero();
        assertThat(reload(username).getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("quando o prazo do bloqueio termina, a conta volta a entrar")
    void lockExpires() throws Exception {
        String username = "expire-" + uid();
        AppUser user = ensureUser(username, Role.OPERADOR, PASSWORD, true);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        users.save(user);

        login(username, PASSWORD, 200);
    }

    @Test
    @DisplayName("usuário inexistente não é bloqueado nem causa erro")
    void unknownUserIsIgnored() throws Exception {
        String username = "ghost-" + uid();

        for (int i = 0; i < 6; i++) {
            login(username, "qualquer", 401);
        }

        assertThat(users.findByUsername(username)).isEmpty();
        assertThat(hasAudit(AuditOperation.ACCOUNT_LOCKED, username)).isFalse();
    }

    @Test
    @DisplayName("bloquear uma conta não afeta as outras")
    void lockDoesNotAffectOtherUsers() throws Exception {
        String locked = "a-" + uid();
        String other = "b-" + uid();
        ensureUser(locked, Role.OPERADOR, PASSWORD, true);
        ensureUser(other, Role.OPERADOR, PASSWORD, true);

        for (int i = 0; i < 5; i++) {
            login(locked, "errada", 401);
        }

        login(other, PASSWORD, 200);
    }
}
