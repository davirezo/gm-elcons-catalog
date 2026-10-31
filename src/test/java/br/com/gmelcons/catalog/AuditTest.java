package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auditoria")
class AuditTest extends IntegrationTestBase {

    @Test
    @DisplayName("cadastro, alteração e atributos geram registros com usuário, entidade e valores")
    void criticalOperationsAreRecorded() throws Exception {
        String sku = "AUD-" + uid();
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão", "NUMBER", true);
        long product = createProduct(cat, sku);

        mvc.perform(put("/api/products/" + product).with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Nome alterado", "sku", sku, "brand", "Marca X"))))
                .andExpect(status().isOk());
        long source = addSource(product, "FABRICANTE");
        putAttributes(product, def, "127", source, true);
        putAttributes(product, def, "220", source, true);

        assertThat(hasAudit(AuditOperation.PRODUCT_CREATE, "Product", product)).isTrue();
        assertThat(hasAudit(AuditOperation.PRODUCT_UPDATE, "Product", product)).isTrue();
        assertThat(hasAudit(AuditOperation.SOURCE_CREATE, "InformationSource", source)).isTrue();
        assertThat(hasAudit(AuditOperation.ATTRIBUTE_UPDATE, "Product", product)).isTrue();

        JsonNode page = read(mvc.perform(get("/api/audit?operation=ATTRIBUTE_UPDATE&username=" + ADMIN + "&size=100").with(asAdmin()))
                .andExpect(status().isOk()).andReturn());
        JsonNode lastChange = null;
        for (JsonNode e : page.get("content")) {
            if (e.get("entityId").asLong() == product) {
                lastChange = e; // lista em ordem decrescente: o primeiro é o mais recente
                break;
            }
        }
        assertThat(lastChange).isNotNull();
        assertThat(lastChange.get("username").asText()).isEqualTo(ADMIN);
        assertThat(lastChange.get("occurredAt").asText()).isNotBlank();
        assertThat(lastChange.get("oldValue").asText()).contains("127");
        assertThat(lastChange.get("newValue").asText()).contains("220");
    }

    @Test
    @DisplayName("exclusão de produto é registrada com os valores anteriores")
    void deletionIsRecorded() throws Exception {
        String sku = "DEL-" + uid();
        long cat = createCategory("Cat-" + uid());
        long product = createProduct(cat, sku);

        mvc.perform(delete("/api/products/" + product + "?confirmSku=" + sku).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(auditLogs.findAll()).anyMatch(a -> a.getOperation() == AuditOperation.PRODUCT_DELETE
                && Long.valueOf(product).equals(a.getEntityId())
                && a.getOldValue() != null && a.getOldValue().contains(sku) && a.getNewValue() == null);
    }

    @Test
    @DisplayName("operação recusada (SKU duplicado) não gera registro de cadastro")
    void failedOperationLeavesNoRecord() throws Exception {
        String sku = "FAIL-" + uid();
        long cat = createCategory("Cat-" + uid());
        createProduct(cat, sku);
        long before = auditLogs.findAll().stream().filter(a -> a.getOperation() == AuditOperation.PRODUCT_CREATE).count();

        mvc.perform(post("/api/products").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Duplicado", "sku", sku, "categoryId", cat))))
                .andExpect(status().isConflict());

        long after = auditLogs.findAll().stream().filter(a -> a.getOperation() == AuditOperation.PRODUCT_CREATE).count();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("somente ADMIN consulta a auditoria")
    void onlyAdminCanQuery() throws Exception {
        mvc.perform(get("/api/audit").with(asOperator())).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").with(asAdmin())).andExpect(status().isOk());
    }
}
