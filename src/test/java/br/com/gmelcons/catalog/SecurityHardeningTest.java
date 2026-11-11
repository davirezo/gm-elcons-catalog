package br.com.gmelcons.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Reforço de segurança: erros sem vazamento e endpoints protegidos")
class SecurityHardeningTest extends IntegrationTestBase {

    private static final String[] PROTECTED_GETS = {
            "/api/products", "/api/categories", "/api/sources", "/api/listings",
            "/api/dashboard", "/api/users", "/api/audit", "/api/auth/me"};

    @Test
    @DisplayName("sem login, todos os endpoints da API respondem 401")
    void anonymousGets401OnEveryEndpoint() throws Exception {
        for (String url : PROTECTED_GETS) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("JSON malformado devolve 400 genérico, sem stack trace nem nome de classe")
    void malformedJsonDoesNotLeakInternals() throws Exception {
        MvcResult r = mvc.perform(post("/api/categories").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{ isto não é json"))
                .andExpect(status().isBadRequest()).andReturn();
        assertNoLeak(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("tipo de conteúdo não suportado devolve 415, não 500")
    void unsupportedMediaTypeIs415() throws Exception {
        MvcResult r = mvc.perform(post("/api/categories").with(asAdmin()).with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andReturn();
        assertNoLeak(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("parâmetro inválido devolve 400 genérico")
    void invalidParameterIs400() throws Exception {
        MvcResult r = mvc.perform(get("/api/products").with(asAdmin()).param("categoryId", "abc"))
                .andExpect(status().isBadRequest()).andReturn();
        assertNoLeak(r.getResponse().getContentAsString());
    }

    private static void assertNoLeak(String body) {
        assertThat(body).doesNotContain("Exception", "at br.com", "org.springframework", "java.lang", "\tat ");
    }
}
