package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.dto.Dtos.*;
import br.com.gmelcons.catalog.service.BriefingService;
import br.com.gmelcons.catalog.service.ListingService;
import br.com.gmelcons.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produtos")
public class ProductController {

    private final ProductService service;
    private final BriefingService briefingService;
    private final ListingService listingService;

    @GetMapping
    public PageResponse<ProductSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String brand,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) ProductStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(q, brand, categoryId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail create(@Valid @RequestBody ProductCreateRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest req) {
        return service.update(id, req);
    }

    /** Exclusão (ADMIN): exige o SKU do produto em confirmSku. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam String confirmSku) {
        service.delete(id, confirmSku);
    }

    @PutMapping("/{id}/attributes")
    public ProductDetail updateAttributes(@PathVariable Long id, @Valid @RequestBody AttributeValuesRequest req) {
        return service.updateAttributes(id, req);
    }

    @GetMapping("/{id}/validation")
    public ValidationResult validation(@PathVariable Long id) {
        return service.validate(id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail duplicate(@PathVariable Long id, @Valid @RequestBody DuplicateRequest req) {
        return service.duplicate(id, req);
    }

    @PostMapping("/{id}/submit-review")
    public ProductDetail submitReview(@PathVariable Long id) {
        return service.submitReview(id);
    }

    @PostMapping("/{id}/approve")
    public ProductDetail approve(@PathVariable Long id, @RequestBody ApproveRequest req) {
        return service.approve(id, req);
    }

    @PostMapping("/{id}/reopen")
    public ProductDetail reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @GetMapping("/{id}/briefing")
    public BriefingDto briefing(@PathVariable Long id) {
        return briefingService.generate(id);
    }

    @GetMapping("/{id}/listing")
    public ListingDto getListing(@PathVariable Long id) {
        return listingService.getByProduct(id);
    }

    @PutMapping("/{id}/listing")
    public ListingDto saveListing(@PathVariable Long id, @Valid @RequestBody ListingRequest req) {
        return listingService.save(id, req);
    }
}
