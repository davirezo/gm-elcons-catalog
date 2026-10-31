package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Produtos: cadastro, validação, duplicação e status")
class ProductWorkflowTest extends IntegrationTestBase {

    /** Produto pronto para anúncio: 1 atributo obrigatório preenchido, com fonte oficial, revisado e aprovado. */
    record Ready(long categoryId, long productId, long definitionId, long sourceId, String sku) {
    }

    private Ready readyProduct() throws Exception {
        String sku = "OK-" + uid();
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão nominal", "NUMBER", true);
        long product = createProduct(cat, sku);
        long source = addSource(product, "FABRICANTE");
        putAttributes(product, def, "127", source, true).getResponse();
        mvc.perform(post("/api/products/" + product + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/products/" + product + "/approve").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmed", true))))
                .andExpect(status().isOk());
        return new Ready(cat, product, def, source, sku);
    }

    // ------------------------------------------------------------ cadastro e edição

    @Test
    @DisplayName("produto nasce INCOMPLETO, com responsável e datas, e o cadastro é auditado")
    void createsIncompleteProduct() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long id = createProduct(cat, "NEW-" + uid());

        JsonNode p = getProduct(id);
        assertThat(p.get("status").asText()).isEqualTo("INCOMPLETO");
        assertThat(p.get("createdBy").asText()).isEqualTo(ADMIN);
        assertThat(p.get("createdAt").asText()).isNotBlank();
        assertThat(p.get("updatedAt").asText()).isNotBlank();
        assertThat(hasAudit(AuditOperation.PRODUCT_CREATE, "Product", id)).isTrue();
    }

    @Test
    @DisplayName("SKU duplicado é recusado (409) e campos obrigatórios são validados (400)")
    void validatesInput() throws Exception {
        String sku = "DUP-" + uid();
        long cat = createCategory("Cat-" + uid());
        createProduct(cat, sku);

        mvc.perform(post("/api/products").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Outro", "sku", sku.toLowerCase(), "categoryId", cat))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/products").with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "", "sku", "X-" + uid(), "categoryId", cat))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("edição altera os dados e registra valores anteriores e novos")
    void editsProduct() throws Exception {
        String sku = "EDT-" + uid();
        long cat = createCategory("Cat-" + uid());
        long id = createProduct(cat, sku);

        mvc.perform(put("/api/products/" + id).with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nome novo", "sku", sku, "brand", "Marca Y", "description", "obs"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome novo"))
                .andExpect(jsonPath("$.brand").value("Marca Y"));
        assertThat(hasAudit(AuditOperation.PRODUCT_UPDATE, "Product", id)).isTrue();
    }

    @Test
    @DisplayName("pesquisa por nome, código, marca e categoria")
    void searchesProducts() throws Exception {
        String tag = uid();
        long cat = createCategory("Cat-" + tag);
        long id = createProduct(cat, "BUSCA-" + tag);
        mvc.perform(put("/api/products/" + id).with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Produto " + tag, "sku", "BUSCA-" + tag, "brand", "Marca-" + tag)))).andReturn();

        mvc.perform(get("/api/products?q=BUSCA-" + tag).with(asOperator())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products?brand=marca-" + tag).with(asOperator()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products?categoryId=" + cat + "&status=INCOMPLETO").with(asOperator()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products?categoryId=" + cat + "&status=PRONTO_PARA_ANUNCIO").with(asOperator()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ atributos obrigatórios

    @Test
    @DisplayName("atributo obrigatório vazio bloqueia o envio para revisão")
    void requiredAttributeBlocksReview() throws Exception {
        long cat = createCategory("Cat-" + uid());
        addAttribute(cat, "Corrente nominal", "NUMBER", true);
        long id = createProduct(cat, "REQ-" + uid());
        addSource(id, "FABRICANTE");

        mvc.perform(post("/api/products/" + id + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Corrente nominal")));
        assertThat(getProduct(id).get("status").asText()).isEqualTo("INCOMPLETO");
    }

    @Test
    @DisplayName("produto sem nenhuma fonte não pode ir para revisão")
    void sourceIsRequired() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão", "NUMBER", true);
        long id = createProduct(cat, "SRC-" + uid());
        putAttributes(id, def, "127", null, true);

        mvc.perform(post("/api/products/" + id + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("fonte")));
    }

    @Test
    @DisplayName("valor numérico inválido é recusado e não é gravado")
    void numericAttributeMustBeNumber() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão", "NUMBER", true);
        long id = createProduct(cat, "NUM-" + uid());

        MvcResult r = putAttributes(id, def, "abc", null, true);
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(getProduct(id).get("attributes").get(0).get("value").isNull()).isTrue();

        // vírgula decimal é aceita e normalizada
        assertThat(putAttributes(id, def, "12,5", null, true).getResponse().getStatus()).isEqualTo(200);
        assertThat(getProduct(id).get("attributes").get(0).get("value").asText()).isEqualTo("12.5");
    }

    @Test
    @DisplayName("fonte de outro produto não pode ser usada em um atributo")
    void sourceMustBelongToProduct() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão", "NUMBER", true);
        long a = createProduct(cat, "A-" + uid());
        long b = createProduct(cat, "B-" + uid());
        long sourceOfA = addSource(a, "FABRICANTE");

        assertThat(putAttributes(b, def, "127", sourceOfA, true).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("atributo baseado só em anúncio de concorrente gera aviso")
    void competitorSourceGeneratesWarning() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão", "NUMBER", true);
        long id = createProduct(cat, "CMP-" + uid());
        long competitor = addSource(id, "ANUNCIO_CONCORRENTE");
        putAttributes(id, def, "127", competitor, true);

        JsonNode warnings = getProduct(id).get("validation").get("warnings");
        assertThat(warnings.toString()).contains("concorrente");
    }

    // ------------------------------------------------------------ status

    @Test
    @DisplayName("fluxo completo: só vira PRONTO_PARA_ANUNCIO após validação e aprovação explícita")
    void readyOnlyAfterExplicitApproval() throws Exception {
        String sku = "FLW-" + uid();
        long cat = createCategory("Cat-" + uid());
        long def = addAttribute(cat, "Tensão nominal", "NUMBER", true);
        long id = createProduct(cat, sku);
        long source = addSource(id, "FABRICANTE");
        putAttributes(id, def, "127", source, true);

        // não é possível aprovar sem antes enviar para revisão
        mvc.perform(post("/api/products/" + id + "/approve").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmed", true))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/products/" + id + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGUARDANDO_REVISAO"));

        // aprovação sem confirmação explícita é recusada
        mvc.perform(post("/api/products/" + id + "/approve").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmed", false))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(getProduct(id).get("status").asText()).isEqualTo("AGUARDANDO_REVISAO");

        mvc.perform(post("/api/products/" + id + "/approve").with(asOperator()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRONTO_PARA_ANUNCIO"))
                .andExpect(jsonPath("$.reviewedBy").value(OPERATOR));

        assertThat(hasAudit(AuditOperation.STATUS_CHANGE, "Product", id)).isTrue();
        long statusChanges = auditLogs.findAll().stream()
                .filter(a -> a.getOperation() == AuditOperation.STATUS_CHANGE && Long.valueOf(id).equals(a.getEntityId())).count();
        assertThat(statusChanges).isEqualTo(2);
    }

    @Test
    @DisplayName("editar um produto PRONTO volta para AGUARDANDO_REVISAO")
    void editingReadyProductRequiresNewReview() throws Exception {
        Ready r = readyProduct();
        putAttributes(r.productId(), r.definitionId(), "220", r.sourceId(), true);

        JsonNode p = getProduct(r.productId());
        assertThat(p.get("status").asText()).isEqualTo("AGUARDANDO_REVISAO");
        assertThat(p.get("reviewedBy").isNull()).isTrue();
    }

    @Test
    @DisplayName("esvaziar um atributo obrigatório volta o produto para INCOMPLETO")
    void clearingRequiredAttributeMakesProductIncomplete() throws Exception {
        Ready r = readyProduct();
        putAttributes(r.productId(), r.definitionId(), "", null, true);
        assertThat(getProduct(r.productId()).get("status").asText()).isEqualTo("INCOMPLETO");
    }

    @Test
    @DisplayName("reabrir um produto o devolve para INCOMPLETO")
    void reopenReturnsToIncomplete() throws Exception {
        Ready r = readyProduct();
        mvc.perform(post("/api/products/" + r.productId() + "/reopen").with(asAdmin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETO"));
    }

    // ------------------------------------------------------------ duplicação

    @Test
    @DisplayName("duplicar: novo produto indica a origem, nasce INCOMPLETO e exige revisão dos atributos copiados")
    void duplicateRequiresReview() throws Exception {
        Ready origin = readyProduct();
        String newSku = "COPIA-" + uid();

        MvcResult res = mvc.perform(post("/api/products/" + origin.productId() + "/duplicate").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("sku", newSku))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode copy = read(res);
        long copyId = copy.get("id").asLong();

        assertThat(copy.get("status").asText()).isEqualTo("INCOMPLETO");
        assertThat(copy.get("duplicatedFromSku").asText()).isEqualTo(origin.sku());
        assertThat(copy.get("duplicatedFromId").asLong()).isEqualTo(origin.productId());
        assertThat(copy.get("attributes").get(0).get("value").asText()).isEqualTo("127");
        assertThat(copy.get("attributes").get(0).get("needsReview").asBoolean()).isTrue();
        assertThat(copy.get("sources")).hasSize(1);
        assertThat(copy.get("validation").get("blockers").toString()).contains("não revisado");
        assertThat(hasAudit(AuditOperation.PRODUCT_DUPLICATE, "Product", copyId)).isTrue();

        // o original continua pronto e intacto
        assertThat(getProduct(origin.productId()).get("status").asText()).isEqualTo("PRONTO_PARA_ANUNCIO");

        // não pode ser enviado para revisão enquanto os atributos não forem revisados
        mvc.perform(post("/api/products/" + copyId + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

        // após confirmar os dados específicos do novo modelo, segue o fluxo normal
        long copiedSource = copy.get("attributes").get(0).get("sourceId").asLong();
        assertThat(putAttributes(copyId, origin.definitionId(), "220", copiedSource, true).getResponse().getStatus()).isEqualTo(200);
        mvc.perform(post("/api/products/" + copyId + "/submit-review").with(asAdmin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGUARDANDO_REVISAO"));
    }

    @Test
    @DisplayName("duplicar com SKU já existente é recusado")
    void duplicateWithExistingSkuFails() throws Exception {
        Ready origin = readyProduct();
        mvc.perform(post("/api/products/" + origin.productId() + "/duplicate").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("sku", origin.sku()))))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ exclusão

    @Test
    @DisplayName("exclusão exige ADMIN e confirmação do SKU")
    void deleteRequiresAdminAndConfirmation() throws Exception {
        String sku = "RM-" + uid();
        long cat = createCategory("Cat-" + uid());
        long id = createProduct(cat, sku);

        mvc.perform(delete("/api/products/" + id + "?confirmSku=" + sku).with(asOperator()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/" + id + "?confirmSku=errado").with(asAdmin()).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/products/" + id).with(asAdmin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/products/" + id + "?confirmSku=" + sku).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id).with(asAdmin())).andExpect(status().isNotFound());
        assertThat(hasAudit(AuditOperation.PRODUCT_DELETE, "Product", id)).isTrue();
    }

    // ------------------------------------------------------------ briefing e anúncio

    @Test
    @DisplayName("briefing só é gerado para produtos prontos e traz atributos, fontes e orientações")
    void briefingRequiresReadyProduct() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long incomplete = createProduct(cat, "INC-" + uid());
        mvc.perform(get("/api/products/" + incomplete + "/briefing").with(asAdmin())).andExpect(status().isConflict());

        Ready r = readyProduct();
        MvcResult res = mvc.perform(get("/api/products/" + r.productId() + "/briefing").with(asAdmin()))
                .andExpect(status().isOk()).andReturn();
        String text = read(res).get("text").asText();
        assertThat(text).contains(r.sku()).contains("Tensão nominal: 127").contains("Fabricante")
                .contains("TÍTULO").contains("DESCRIÇÃO").contains("PALAVRAS-CHAVE");
    }

    @Test
    @DisplayName("anúncio só é salvo para produto pronto e guarda título, descrição, palavras-chave e revisor")
    void listingIsStoredOnlyForReadyProducts() throws Exception {
        long cat = createCategory("Cat-" + uid());
        long incomplete = createProduct(cat, "LST-" + uid());
        Map<String, String> body = Map.of("title", "Título de teste", "description", "Descrição de teste", "keywords", "a, b, c");
        mvc.perform(put("/api/products/" + incomplete + "/listing").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isConflict());

        Ready r = readyProduct();
        mvc.perform(put("/api/products/" + r.productId() + "/listing").with(asOperator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Título de teste"))
                .andExpect(jsonPath("$.reviewedBy").value(OPERATOR))
                .andExpect(jsonPath("$.preparedAt").isNotEmpty());
        mvc.perform(get("/api/products/" + r.productId() + "/listing").with(asAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.keywords").value("a, b, c"));
        assertThat(hasAudit(AuditOperation.LISTING_CREATE, OPERATOR)).isTrue();

        mvc.perform(put("/api/products/" + r.productId() + "/listing").with(asAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "Novo título", "description", "Outra descrição"))))
                .andExpect(status().isOk());
        assertThat(hasAudit(AuditOperation.LISTING_UPDATE, ADMIN)).isTrue();
    }

    // ------------------------------------------------------------ dashboard

    @Test
    @DisplayName("dashboard reflete os dados reais do banco")
    void dashboardUsesRealData() throws Exception {
        MvcResult before = mvc.perform(get("/api/dashboard").with(asAdmin())).andExpect(status().isOk()).andReturn();
        long total = read(before).get("total").asLong();
        long ready = read(before).get("pronto").asLong();

        readyProduct();

        JsonNode after = read(mvc.perform(get("/api/dashboard").with(asAdmin())).andReturn());
        assertThat(after.get("total").asLong()).isEqualTo(total + 1);
        assertThat(after.get("pronto").asLong()).isEqualTo(ready + 1);
        assertThat(after.get("recentActivity").size()).isGreaterThan(0);
    }
}
