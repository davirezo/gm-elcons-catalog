package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.Role;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Trilha de auditoria (JPA Auditing e login com IP)")
class AuditTrailTest extends IntegrationTestBase {

    @Autowired ProductRepository products;

    @Test
    @DisplayName("login com sucesso e com falha registram usuário e IP")
    void loginEventsRecordUserAndIp() throws Exception {
        String username = "aud-" + uid();
        ensureUser(username, Role.OPERADOR, "Senha@12345", true);

        mvc.perform(post("/api/auth/login").param("username", username).param("password", "errada").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("username", username).param("password", "Senha@12345").with(csrf()))
                .andExpect(status().isOk());

        var logs = auditLogs.findAll().stream().filter(a -> username.equals(a.getUsername())).toList();
        assertThat(logs).extracting(a -> a.getOperation())
                .contains(AuditOperation.LOGIN_FAILED, AuditOperation.LOGIN);
        assertThat(logs).allSatisfy(a -> assertThat(a.getIpAddress()).isNotBlank());
    }

    @Test
    @DisplayName("alterar produto preenche lastModifiedBy com o usuário logado")
    void productUpdateFillsLastModifiedBy() throws Exception {
        long cat = createCategory("Cat " + uid());
        String sku = "AUD-" + uid();
        long id = createProduct(cat, sku);

        mvc.perform(put("/api/products/" + id).with(asOperator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Alterado", "sku", sku, "brand", "Marca", "description", "x"))))
                .andExpect(status().isOk());

        Product p = products.findById(id).orElseThrow();
        assertThat(p.getLastModifiedBy()).isEqualTo(OPERATOR);
        assertThat(p.getUpdatedAt()).isNotNull();
    }
}
