package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.dto.Dtos.ProductImageDto;
import br.com.gmelcons.catalog.service.ProductImageService;
import br.com.gmelcons.catalog.service.ProductImageService.ImageContent;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Imagens")
public class ProductImageController {

    private final ProductImageService service;

    @GetMapping("/api/products/{productId}/images")
    public List<ProductImageDto> list(@PathVariable Long productId) {
        return service.list(productId);
    }

    @PostMapping(path = "/api/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageDto upload(@PathVariable Long productId, @RequestParam("file") MultipartFile file) {
        return service.add(productId, file);
    }

    /** Conteúdo da imagem. Exige login; o tipo vem do banco (detectado no envio) e o navegador não pode "adivinhar" outro. */
    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        ImageContent c = service.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(c.data());
    }

    @PutMapping("/api/images/{id}/main")
    public List<ProductImageDto> setMain(@PathVariable Long id) {
        return service.setMain(id);
    }

    @DeleteMapping("/api/images/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
