package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.entity.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Autorização por papel (RBAC)")
class AuthorizationTest extends IntegrationTestBase {

    @Test
    @DisplayName("OPERADOR não acessa usuários nem auditoria")
    void operatorCannotAccessAdminAreas() throws Exception {
        mvc.perform(get("/api/users").with(asOperator())).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").with(asOperator())).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").with(asOperator()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "x-user", "fullName", "X", "password", "Senha@12345", "role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERADOR não altera categorias nem atributos")
    void operatorCannotChangeCategories() throws Exception {
        mvc.perform(post("/api/categories").with(asOperator()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Não pode " + uid())))).andExpect(status().isForbidden());
        mvc.perform(put("/api/attribute-definitions/1").with(asOperator()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "x", "dataType", "TEXT", "required", false)))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/categories/1").with(asOperator()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERADOR não exclui produtos")
    void operatorCannotDeleteProducts() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long product = createProduct(cat, "SKU-" + uid());
        mvc.perform(delete("/api/products/" + product + "?confirmSku=qualquer").with(asOperator()).with(csrf()))
                .andExpect(status().isForbidden());
        getProduct(product); // continua existindo
    }

    @Test
    @DisplayName("OPERADOR consulta e cadastra produtos normalmente")
    void operatorCanWorkWithProducts() throws Exception {
        long cat = createCategory("Cat-" + uid());
        mvc.perform(get("/api/categories").with(asOperator())).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").with(asOperator())).andExpect(status().isOk());
        mvc.perform(get("/api/products").with(asOperator())).andExpect(status().isOk());
        MvcResult r = mvc.perform(post("/api/products").with(asOperator()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Feito pelo operador", "sku", "OP-" + uid(), "categoryId", cat))))
                .andExpect(status().isCreated()).andReturn();
        assertThat(read(r).get("createdBy").asText()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("ADMIN acessa usuários e auditoria")
    void adminCanAccessAdminAreas() throws Exception {
        mvc.perform(get("/api/users").with(asAdmin())).andExpect(status().isOk());
        mvc.perform(get("/api/audit").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("requisições que alteram dados sem CSRF são recusadas")
    void mutatingRequestsRequireCsrf() throws Exception {
        mvc.perform(post("/api/categories").with(asAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Sem csrf " + uid())))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN cria usuário: senha em BCrypt, fora da resposta e fora da auditoria")
    void adminCreatesUserWithoutLeakingPassword() throws Exception {
        String username = "novo-" + uid();
        MvcResult r = mvc.perform(post("/api/users").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "fullName", "Novo Usuário",
                                "password", "SenhaSecreta@99", "role", "OPERADOR"))))
                .andExpect(status().isCreated()).andReturn();

        String body = r.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SenhaSecreta@99").doesNotContain("passwordHash").doesNotContain("$2");

        AppUser saved = users.findByUsername(username).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(encoder.matches("SenhaSecreta@99", saved.getPasswordHash())).isTrue();

        assertThat(hasAudit(AuditOperation.USER_CREATE, ADMIN)).isTrue();
        assertThat(auditLogs.findAll()).noneMatch(a ->
                (a.getNewValue() != null && a.getNewValue().contains("SenhaSecreta@99"))
                        || (a.getNewValue() != null && a.getNewValue().contains("$2a$")));
    }

    @Test
    @DisplayName("login duplicado é recusado e senha curta é rejeitada")
    void userValidation() throws Exception {
        String username = "dup-" + uid();
        String payload = json(Map.of("username", username, "fullName", "Dup", "password", "Senha@12345", "role", "OPERADOR"));
        mvc.perform(post("/api/users").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/users").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "curta-" + uid(), "fullName", "Curta", "password", "123", "role", "OPERADOR"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADMIN não pode desativar a própria conta")
    void adminCannotDisableSelf() throws Exception {
        AppUser admin = users.findByUsername(ADMIN).orElseThrow();
        mvc.perform(put("/api/users/" + admin.getId()).with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Admin", "role", "ADMIN", "enabled", false))))
                .andExpect(status().isConflict());
        assertThat(users.findByUsername(ADMIN).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("alteração de papel é auditada com valores anteriores e novos")
    void roleChangeIsAudited() throws Exception {
        String username = "papel-" + uid();
        AppUser target = ensureUser(username, br.com.gmelcons.catalog.domain.Role.OPERADOR, "Senha@12345", true);
        mvc.perform(put("/api/users/" + target.getId()).with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Promovido", "role", "ADMIN", "enabled", true))))
                .andExpect(status().isOk());

        JsonNode page = read(mvc.perform(get("/api/audit?operation=USER_UPDATE&size=100").with(asAdmin()))
                .andExpect(status().isOk()).andReturn());
        boolean found = false;
        for (JsonNode e : page.get("content")) {
            if (e.get("entityId").asLong() == target.getId()) {
                found = e.get("oldValue").asText().contains("OPERADOR") && e.get("newValue").asText().contains("ADMIN");
            }
        }
        assertThat(found).isTrue();
    }
}
