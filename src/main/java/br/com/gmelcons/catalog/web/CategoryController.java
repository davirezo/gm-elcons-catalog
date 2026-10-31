package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.dto.Dtos.*;
import br.com.gmelcons.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Categorias e atributos")
public class CategoryController {

    private final CategoryService service;

    @GetMapping("/api/categories")
    public List<CategoryDto> list() {
        return service.list();
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto create(@Valid @RequestBody CategoryRequest req) {
        return service.create(req);
    }

    @PutMapping("/api/categories/{id}")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/api/categories/{categoryId}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeDefinitionDto addAttribute(@PathVariable Long categoryId,
                                               @Valid @RequestBody AttributeDefinitionRequest req) {
        return service.addAttribute(categoryId, req);
    }

    @PutMapping("/api/attribute-definitions/{id}")
    public AttributeDefinitionDto updateAttribute(@PathVariable Long id,
                                                  @Valid @RequestBody AttributeDefinitionRequest req) {
        return service.updateAttribute(id, req);
    }

    @DeleteMapping("/api/attribute-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttribute(@PathVariable Long id) {
        service.deleteAttribute(id);
    }
}
