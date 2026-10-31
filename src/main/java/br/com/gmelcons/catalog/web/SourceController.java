package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.PageResponse;
import br.com.gmelcons.catalog.dto.Dtos.SourceDto;
import br.com.gmelcons.catalog.dto.Dtos.SourceRequest;
import br.com.gmelcons.catalog.service.SourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Fontes de informação")
public class SourceController {

    private final SourceService service;

    @GetMapping("/api/sources")
    public PageResponse<SourceDto> listAll(@RequestParam(required = false) SourceType type,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.listAll(type, page, size);
    }

    @GetMapping("/api/products/{productId}/sources")
    public List<SourceDto> listByProduct(@PathVariable Long productId) {
        return service.listByProduct(productId);
    }

    @PostMapping("/api/products/{productId}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDto add(@PathVariable Long productId, @Valid @RequestBody SourceRequest req) {
        return service.add(productId, req);
    }

    @PutMapping("/api/sources/{id}")
    public SourceDto update(@PathVariable Long id, @Valid @RequestBody SourceRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
