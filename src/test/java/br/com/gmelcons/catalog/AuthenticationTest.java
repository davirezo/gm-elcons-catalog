package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.Role;
import br.com.gmelcons.catalog.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Autenticação")
class AuthenticationTest extends IntegrationTestBase {

    @Test
    @DisplayName("login com credenciais válidas retorna 200 e é auditado")
    void loginWithValidCredentials() throws Exception {
        String username = "login-" + uid();
        ensureUser(username, Role.OPERADOR, "Senha@12345", true);

        mvc.perform(post("/api/auth/login").param("username", username).param("password", "Senha@12345").with(csrf()))
                .andExpect(status().isOk());

        assertThat(hasAudit(AuditOperation.LOGIN, username)).isTrue();
    }

    @Test
    @DisplayName("senha errada retorna 401 e a falha é auditada")
    void loginWithWrongPassword() throws Exception {
        String username = "wrong-" + uid();
        ensureUser(username, Role.OPERADOR, "Senha@12345", true);

        mvc.perform(post("/api/auth/login").param("username", username).param("password", "errada").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("inválidos")));

        assertThat(hasAudit(AuditOperation.LOGIN_FAILED, username)).isTrue();
    }

    @Test
    @DisplayName("usuário inativo não consegue entrar")
    void disabledUserCannotLogin() throws Exception {
        String username = "off-" + uid();
        ensureUser(username, Role.OPERADOR, "Senha@12345", false);

        mvc.perform(post("/api/auth/login").param("username", username).param("password", "Senha@12345").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login sem token CSRF é recusado")
    void loginWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", ADMIN).param("password", "Admin@12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("APIs protegidas exigem autenticação (401)")
    void protectedApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("páginas sem sessão redirecionam para o login")
    void pagesRedirectToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login.html"));
    }

    @Test
    @DisplayName("a página de login é pública")
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("senhas são armazenadas com BCrypt")
    void passwordsAreStoredWithBcrypt() {
        AppUser admin = users.findByUsername(ADMIN).orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getPasswordHash()).doesNotContain("Admin@12345");
        assertThat(encoder.matches("Admin@12345", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("/api/auth/me devolve o usuário autenticado")
    void meReturnsCurrentUser() throws Exception {
        mvc.perform(get("/api/auth/me").with(asOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OPERATOR))
                .andExpect(jsonPath("$.role").value("OPERADOR"));
    }
}
