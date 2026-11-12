package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.repository.ProductImageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Imagens do produto")
class ProductImageTest extends IntegrationTestBase {

    @Autowired ProductImageRepository imageRepo;

    // ---------------------------------------------------------------- apoio

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static byte[] png() throws Exception {
        return image("png", 8, 8);
    }

    private long newProduct() throws Exception {
        return createProduct(createCategory("Cat " + uid()), "IMG-" + uid());
    }

    private ResultActions upload(long productId, String name, String declaredType, byte[] bytes,
                                 RequestPostProcessor user) throws Exception {
        return mvc.perform(multipart("/api/products/" + productId + "/images")
                .file(new MockMultipartFile("file", name, declaredType, bytes)).with(user).with(csrf()));
    }

    private long uploadOk(long productId, byte[] bytes) throws Exception {
        MvcResult r = upload(productId, "foto.png", "image/png", bytes, asOperator())
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    private JsonNode list(long productId) throws Exception {
        return read(mvc.perform(get("/api/products/" + productId + "/images").with(asOperator()))
                .andExpect(status().isOk()).andReturn());
    }

    // ---------------------------------------------------------------- testes

    @Test
    @DisplayName("operador envia PNG válido; a lista e o conteúdo voltam corretos, com nosniff e auditoria")
    void validUploadIsListedServedAndAudited() throws Exception {
        long product = newProduct();
        byte[] bytes = png();

        MvcResult r = upload(product, "frente.png", "image/png", bytes, asOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.main").value(true))
                .andExpect(jsonPath("$.fileName").value("frente.png")).andReturn();
        long id = read(r).get("id").asLong();

        assertThat(list(product)).hasSize(1);
        MvcResult content = mvc.perform(get("/api/images/" + id).with(asOperator()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertThat(content.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(hasAudit(AuditOperation.IMAGE_ADD, "Product", product)).isTrue();
    }

    @Test
    @DisplayName("texto disfarçado de imagem (nome .png e Content-Type image/png) é recusado")
    void textDisguisedAsImageIsRejected() throws Exception {
        long product = newProduct();
        upload(product, "evil.png", "image/png", "isto não é uma imagem".getBytes(StandardCharsets.UTF_8), asOperator())
                .andExpect(status().isUnprocessableEntity());
        assertThat(list(product)).isEmpty();
    }

    @Test
    @DisplayName("SVG é recusado (pode conter script)")
    void svgIsRejected() throws Exception {
        long product = newProduct();
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        upload(product, "x.svg", "image/svg+xml", svg.getBytes(StandardCharsets.UTF_8), asOperator())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("o tipo guardado vem do conteúdo, não do Content-Type nem do nome enviados")
    void typeComesFromContentNotFromClient() throws Exception {
        long product = newProduct();
        MvcResult r = upload(product, "../../x.html", "text/html", png(), asOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png")).andReturn();
        assertThat(read(r).get("fileName").asText()).doesNotContain("/").doesNotContain("..");
        long id = read(r).get("id").asLong();
        mvc.perform(get("/api/images/" + id).with(asOperator())).andExpect(content().contentType("image/png"));
    }

    @Test
    @DisplayName("JPEG e WebP também são aceitos")
    void jpegAndWebpAreAccepted() throws Exception {
        long product = newProduct();
        upload(product, "a.jpg", "image/jpeg", image("jpg", 8, 8), asOperator())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/jpeg"));
        byte[] webp = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        upload(product, "a.webp", "image/webp", webp, asOperator())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/webp"));
    }

    @Test
    @DisplayName("no máximo 6 imagens por produto")
    void maxSixImagesPerProduct() throws Exception {
        long product = newProduct();
        for (int i = 0; i < 6; i++) {
            uploadOk(product, png());
        }
        upload(product, "sete.png", "image/png", png(), asOperator()).andExpect(status().isConflict());
        assertThat(list(product)).hasSize(6);
    }

    @Test
    @DisplayName("arquivo acima de 5 MB é recusado com 413")
    void oversizedFileIsRejected() throws Exception {
        long product = newProduct();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(png(), 0, big, 0, 8);
        upload(product, "grande.png", "image/png", big, asOperator()).andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("tornar principal reordena; remover a principal promove a próxima; tudo é auditado")
    void setMainAndDelete() throws Exception {
        long product = newProduct();
        long first = uploadOk(product, png());
        long second = uploadOk(product, png());
        assertThat(list(product).get(0).get("id").asLong()).isEqualTo(first);

        mvc.perform(put("/api/images/" + second + "/main").with(asOperator()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].main").value(true)).andExpect(jsonPath("$[1].main").value(false));

        // a consulta usada para a miniatura nos anúncios devolve a mesma principal
        var rows = imageRepo.findOrderedIds(List.of(product));
        assertThat(rows.get(0).getId()).isEqualTo(second);

        mvc.perform(delete("/api/images/" + second).with(asOperator()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/images/" + second).with(asOperator())).andExpect(status().isNotFound());
        JsonNode after = list(product);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("id").asLong()).isEqualTo(first);
        assertThat(after.get(0).get("main").asBoolean()).isTrue();

        assertThat(hasAudit(AuditOperation.IMAGE_SET_MAIN, "Product", product)).isTrue();
        assertThat(hasAudit(AuditOperation.IMAGE_REMOVE, "Product", product)).isTrue();
    }

    @Test
    @DisplayName("exige login e token CSRF")
    void requiresLoginAndCsrf() throws Exception {
        long product = newProduct();
        long id = uploadOk(product, png());
        mvc.perform(get("/api/images/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/" + product + "/images")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/products/" + product + "/images")
                .file(new MockMultipartFile("file", "a.png", "image/png", png())).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/products/" + product + "/images")
                .file(new MockMultipartFile("file", "a.png", "image/png", png())).with(asOperator()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/images/" + id).with(asOperator())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem o campo 'file' devolve 400 genérico")
    void missingFilePartIsBadRequest() throws Exception {
        long product = newProduct();
        MvcResult r = mvc.perform(multipart("/api/products/" + product + "/images").with(asOperator()).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("Exception");
    }

    @Test
    @DisplayName("excluir o produto remove as imagens dele")
    void deletingProductRemovesImages() throws Exception {
        String sku = "IMG-DEL-" + uid();
        long product = createProduct(createCategory("Cat " + uid()), sku);
        long id = uploadOk(product, png());

        mvc.perform(delete("/api/products/" + product).param("confirmSku", sku).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/images/" + id).with(asAdmin())).andExpect(status().isNotFound());
    }
}
