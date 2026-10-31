package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.Role;
import br.com.gmelcons.catalog.entity.AppUser;
import br.com.gmelcons.catalog.repository.AppUserRepository;
import br.com.gmelcons.catalog.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: sobe a aplicação com H2 em memória (perfil "test"),
 * sem dados de demonstração. Cada teste usa SKUs/categorias com sufixo único para não interferir nos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    static final String ADMIN = "test-admin";
    static final String OPERATOR = "test-operator";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected AppUserRepository users;
    @Autowired protected AuditLogRepository auditLogs;
    @Autowired protected PasswordEncoder encoder;

    @BeforeEach
    void ensureBaseUsers() {
        ensureUser(ADMIN, Role.ADMIN, "Admin@12345", true);
        ensureUser(OPERATOR, Role.OPERADOR, "Operador@12345", true);
    }

    protected AppUser ensureUser(String username, Role role, String password, boolean enabled) {
        return users.findByUsername(username).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername(username);
            u.setFullName("Usuário de teste " + username);
            u.setPasswordHash(encoder.encode(password));
            u.setRole(role);
            u.setEnabled(enabled);
            return users.save(u);
        });
    }

    protected RequestPostProcessor asAdmin() {
        return user(ADMIN).roles("ADMIN");
    }

    protected RequestPostProcessor asOperator() {
        return user(OPERATOR).roles("OPERADOR");
    }

    protected static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected boolean hasAudit(AuditOperation op, String username) {
        return auditLogs.findAll().stream()
                .anyMatch(a -> a.getOperation() == op && username.equals(a.getUsername()));
    }

    protected boolean hasAudit(AuditOperation op, String entityType, Long entityId) {
        return auditLogs.findAll().stream()
                .anyMatch(a -> a.getOperation() == op && entityType.equals(a.getEntityType())
                        && entityId.equals(a.getEntityId()));
    }

    // ---------------------------------------------------------------- atalhos de API (como ADMIN)

    protected long createCategory(String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/categories").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    protected long addAttribute(long categoryId, String name, String type, boolean required) throws Exception {
        MvcResult r = mvc.perform(post("/api/categories/" + categoryId + "/attributes").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "dataType", type, "required", required))))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    protected long createProduct(long categoryId, String sku) throws Exception {
        MvcResult r = mvc.perform(post("/api/products").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Produto " + sku, "sku", sku, "categoryId", categoryId))))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    protected long addSource(long productId, String type) throws Exception {
        MvcResult r = mvc.perform(post("/api/products/" + productId + "/sources").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", type, "name", "Fonte de teste (" + type + ")"))))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    protected MvcResult putAttributes(long productId, long definitionId, String value, Long sourceId, boolean reviewed)
            throws Exception {
        Map<String, Object> item = new java.util.HashMap<>();
        item.put("definitionId", definitionId);
        item.put("value", value);
        item.put("sourceId", sourceId);
        item.put("reviewed", reviewed);
        return mvc.perform(put("/api/products/" + productId + "/attributes").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", java.util.List.of(item)))))
                .andReturn();
    }

    protected JsonNode getProduct(long productId) throws Exception {
        return read(mvc.perform(get("/api/products/" + productId).with(asAdmin()))
                .andExpect(status().isOk()).andReturn());
    }
}
