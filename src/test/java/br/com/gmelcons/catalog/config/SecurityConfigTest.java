package br.com.gmelcons.catalog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário do repositório do token CSRF. Não usa MockMvc de propósito: o helper csrf() do
 * Spring Security Test troca o repositório no filtro compartilhado, e o cookie real não apareceria.
 */
@DisplayName("Cookie do token CSRF")
class SecurityConfigTest {

    @Test
    @DisplayName("XSRF-TOKEN sai com SameSite=Lax e legível pelo JavaScript (sem HttpOnly)")
    void csrfCookieHasSameSiteLaxAndIsReadableByScript() {
        CookieCsrfTokenRepository repository = SecurityConfig.csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        // Lê o atributo no objeto Cookie: o MockHttpServletResponse não escreve SameSite no cabeçalho
        // para cookies comuns, mas o Tomcat escreve (o atributo é o que o Spring Security define).
        jakarta.servlet.http.Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
