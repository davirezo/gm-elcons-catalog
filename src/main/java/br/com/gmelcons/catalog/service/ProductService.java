package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.dto.Dtos.*;
import br.com.gmelcons.catalog.entity.*;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static br.com.gmelcons.catalog.service.AuditService.fields;
import static br.com.gmelcons.catalog.service.Text.blankToNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AttributeDefinitionRepository definitions;
    private final ProductAttributeValueRepository values;
    private final InformationSourceRepository sources;
    private final AdListingRepository listings;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final ProductValidationService validation;
    private final ProductWorkflowService workflow;

    // ---------------------------------------------------------------- consulta

    public PageResponse<ProductSummary> search(String q, String brand, Long categoryId, ProductStatus status,
                                               int page, int size) {
        Specification<Product> spec = (root, cq, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("sku")), like)));
        }
        if (brand != null && !brand.isBlank()) {
            String like = "%" + brand.trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("brand")), like));
        }
        if (categoryId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (status != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return PageResponse.of(products.findAll(spec, pageable).map(this::toSummary));
    }

    public ProductDetail get(Long id) {
        return detail(find(id));
    }

    public ValidationResult validate(Long id) {
        return validation.validate(find(id));
    }

    // ---------------------------------------------------------------- cadastro

    @Transactional
    public ProductDetail create(ProductCreateRequest req) {
        String sku = req.sku().trim();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("Já existe um produto com este SKU");
        }
        Category category = categories.findById(req.categoryId())
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        Product product = new Product();
        product.setName(req.name().trim());
        product.setSku(sku);
        product.setBrand(blankToNull(req.brand()));
        product.setCategory(category);
        product.setDescription(blankToNull(req.description()));
        product.setStatus(ProductStatus.INCOMPLETO);
        product.setCreatedBy(currentUser.entity());
        products.save(product);
        audit.log(AuditOperation.PRODUCT_CREATE, "Product", product.getId(), null, snapshot(product));
        return detail(product);
    }

    @Transactional
    public ProductDetail update(Long id, ProductUpdateRequest req) {
        Product product = find(id);
        String sku = req.sku().trim();
        if (products.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
            throw BusinessException.conflict("Já existe um produto com este SKU");
        }
        Map<String, Object> before = snapshot(product);
        product.setName(req.name().trim());
        product.setSku(sku);
        product.setBrand(blankToNull(req.brand()));
        product.setDescription(blankToNull(req.description()));
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.PRODUCT_UPDATE, "Product", id, before, snapshot(product));
        workflow.reevaluateAfterEdit(product);
        return detail(product);
    }

    /** Exclusão: somente ADMIN e com confirmação explícita do SKU. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id, String confirmSku) {
        Product product = find(id);
        if (confirmSku == null || !confirmSku.trim().equalsIgnoreCase(product.getSku())) {
            throw BusinessException.unprocessable("Confirmação inválida: informe o SKU do produto para excluí-lo");
        }
        Map<String, Object> before = snapshot(product);
        listings.deleteByProductId(id);
        values.deleteByProductId(id);
        sources.deleteByProductId(id);
        products.delete(product);
        audit.log(AuditOperation.PRODUCT_DELETE, "Product", id, before, null);
    }

    // ---------------------------------------------------------------- atributos

    @Transactional
    public ProductDetail updateAttributes(Long productId, AttributeValuesRequest req) {
        Product product = find(productId);
        Map<Long, AttributeDefinition> defs = new HashMap<>();
        for (AttributeDefinition d : definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(product.getCategory().getId())) {
            defs.put(d.getId(), d);
        }
        Map<Long, ProductAttributeValue> existing = new HashMap<>();
        for (ProductAttributeValue v : values.findByProductId(productId)) {
            existing.put(v.getDefinition().getId(), v);
        }

        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();

        for (AttributeValueItem item : req.items()) {
            AttributeDefinition def = defs.get(item.definitionId());
            if (def == null) {
                throw BusinessException.unprocessable("Atributo não pertence à categoria do produto");
            }
            ProductAttributeValue current = existing.get(def.getId());
            String before = describe(current);
            String normalized = normalize(def, item.value());

            InformationSource source = null;
            if (item.sourceId() != null) {
                source = sources.findById(item.sourceId())
                        .filter(s -> s.getProduct().getId().equals(productId))
                        .orElseThrow(() -> BusinessException.unprocessable("Fonte inválida para este produto"));
            }

            if (normalized == null) {
                if (current != null) {
                    values.delete(current);
                    existing.remove(def.getId());
                    oldValues.put(def.getName(), before);
                    newValues.put(def.getName(), null);
                }
                continue;
            }

            if (current == null) {
                current = new ProductAttributeValue();
                current.setProduct(product);
                current.setDefinition(def);
                current.setNeedsReview(false);
            }
            boolean needsReview = item.reviewed() == null ? current.isNeedsReview() : !item.reviewed();
            current.setValue(normalized);
            current.setSource(source);
            current.setNeedsReview(needsReview);
            values.save(current);
            existing.put(def.getId(), current);

            String after = describe(current);
            if (!Objects.equals(before, after)) {
                oldValues.put(def.getName(), before);
                newValues.put(def.getName(), after);
            }
        }

        if (!newValues.isEmpty()) {
            product.setUpdatedAt(Instant.now());
            audit.log(AuditOperation.ATTRIBUTE_UPDATE, "Product", productId,
                    fields("sku", product.getSku(), "atributos", oldValues),
                    fields("sku", product.getSku(), "atributos", newValues));
            workflow.reevaluateAfterEdit(product);
        }
        return detail(product);
    }

    // ---------------------------------------------------------------- duplicação

    @Transactional
    public ProductDetail duplicate(Long id, DuplicateRequest req) {
        Product origin = find(id);
        String sku = req.sku().trim();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("Já existe um produto com este SKU");
        }
        Product copy = new Product();
        String name = blankToNull(req.name());
        copy.setName(name != null ? name : "Cópia de " + origin.getName());
        copy.setSku(sku);
        copy.setBrand(origin.getBrand());
        copy.setCategory(origin.getCategory());
        copy.setDescription(origin.getDescription());
        copy.setStatus(ProductStatus.INCOMPLETO);
        copy.setCreatedBy(currentUser.entity());
        copy.setDuplicatedFromId(origin.getId());
        copy.setDuplicatedFromSku(origin.getSku());
        products.save(copy);

        Map<Long, InformationSource> sourceMap = new HashMap<>();
        for (InformationSource s : sources.findByProductIdOrderByIdAsc(origin.getId())) {
            InformationSource c = new InformationSource();
            c.setProduct(copy);
            c.setType(s.getType());
            c.setName(s.getName());
            c.setUrl(s.getUrl());
            c.setConsultedAt(s.getConsultedAt());
            c.setNotes(s.getNotes());
            sources.save(c);
            sourceMap.put(s.getId(), c);
        }
        int copied = 0;
        for (ProductAttributeValue v : values.findByProductId(origin.getId())) {
            ProductAttributeValue c = new ProductAttributeValue();
            c.setProduct(copy);
            c.setDefinition(v.getDefinition());
            c.setValue(v.getValue());
            c.setSource(v.getSource() == null ? null : sourceMap.get(v.getSource().getId()));
            c.setNeedsReview(true); // todo valor copiado exige revisão explícita
            values.save(c);
            copied++;
        }
        audit.log(AuditOperation.PRODUCT_DUPLICATE, "Product", copy.getId(),
                fields("origemId", origin.getId(), "origemSku", origin.getSku()),
                fields("sku", copy.getSku(), "atributosCopiados", copied, "fontesCopiadas", sourceMap.size()));
        return detail(copy);
    }

    // ---------------------------------------------------------------- status

    @Transactional
    public ProductDetail submitReview(Long id) {
        Product product = find(id);
        if (product.getStatus() != ProductStatus.INCOMPLETO) {
            throw BusinessException.conflict("Somente produtos incompletos podem ser enviados para revisão");
        }
        ValidationResult result = validation.validate(product);
        if (!result.ready()) {
            throw BusinessException.unprocessable("Não é possível enviar para revisão: " + String.join("; ", result.blockers()));
        }
        workflow.changeStatus(product, ProductStatus.AGUARDANDO_REVISAO, "Enviado para revisão");
        return detail(product);
    }

    @Transactional
    public ProductDetail approve(Long id, ApproveRequest req) {
        Product product = find(id);
        if (!req.confirmed()) {
            throw BusinessException.unprocessable("A aprovação exige a confirmação explícita da revisão");
        }
        if (product.getStatus() != ProductStatus.AGUARDANDO_REVISAO) {
            throw BusinessException.conflict("Somente produtos aguardando revisão podem ser aprovados");
        }
        ValidationResult result = validation.validate(product);
        if (!result.ready()) {
            throw BusinessException.unprocessable("Não é possível aprovar: " + String.join("; ", result.blockers()));
        }
        workflow.changeStatus(product, ProductStatus.PRONTO_PARA_ANUNCIO, "Revisão aprovada");
        product.setReviewedBy(currentUser.entity());
        product.setReviewedAt(Instant.now());
        return detail(product);
    }

    @Transactional
    public ProductDetail reopen(Long id) {
        Product product = find(id);
        if (product.getStatus() == ProductStatus.INCOMPLETO) {
            throw BusinessException.conflict("O produto já está como incompleto");
        }
        workflow.changeStatus(product, ProductStatus.INCOMPLETO, "Reaberto para edição");
        return detail(product);
    }

    // ---------------------------------------------------------------- internos

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produto não encontrado"));
    }

    private ProductSummary toSummary(Product p) {
        return new ProductSummary(p.getId(), p.getName(), p.getSku(), p.getBrand(), p.getCategory().getId(),
                p.getCategory().getName(), p.getStatus(), p.getCreatedBy().getUsername(), p.getUpdatedAt(),
                p.getDuplicatedFromSku());
    }

    private ProductDetail detail(Product p) {
        List<AttributeDefinition> defs = definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(p.getCategory().getId());
        Map<Long, ProductAttributeValue> byDefinition = values.findByProductId(p.getId()).stream()
                .collect(Collectors.toMap(v -> v.getDefinition().getId(), v -> v));
        List<AttributeView> attributes = defs.stream().map(d -> {
            ProductAttributeValue v = byDefinition.get(d.getId());
            return new AttributeView(d.getId(), d.getName(), d.getDataType(), d.getUnit(), d.isRequired(),
                    v == null ? null : v.getValue(),
                    v == null || v.getSource() == null ? null : v.getSource().getId(),
                    v != null && v.isNeedsReview());
        }).toList();
        List<SourceDto> sourceDtos = sources.findByProductIdOrderByIdAsc(p.getId()).stream()
                .map(SourceService::toDto).toList();
        return new ProductDetail(p.getId(), p.getName(), p.getSku(), p.getBrand(), p.getCategory().getId(),
                p.getCategory().getName(), p.getDescription(), p.getStatus(), p.getCreatedBy().getUsername(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getDuplicatedFromId(), p.getDuplicatedFromSku(),
                p.getReviewedBy() == null ? null : p.getReviewedBy().getUsername(), p.getReviewedAt(),
                attributes, sourceDtos, validation.validate(p), listings.existsByProductId(p.getId()));
    }

    private static Map<String, Object> snapshot(Product p) {
        return fields("name", p.getName(), "sku", p.getSku(), "brand", p.getBrand(),
                "category", p.getCategory().getName(), "description", p.getDescription(), "status", p.getStatus());
    }

    private static String describe(ProductAttributeValue v) {
        if (v == null) {
            return null;
        }
        return v.getValue() + " | fonte=" + (v.getSource() == null ? "-" : v.getSource().getId())
                + " | revisar=" + v.isNeedsReview();
    }

    /** Valida o valor conforme o tipo do atributo e devolve a forma normalizada (ou null se vazio). */
    private static String normalize(AttributeDefinition def, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        return switch (def.getDataType()) {
            case TEXT -> v;
            case NUMBER -> {
                String n = v.replace(',', '.');
                try {
                    new BigDecimal(n);
                } catch (NumberFormatException e) {
                    throw BusinessException.unprocessable("O atributo '" + def.getName() + "' deve ser numérico");
                }
                yield n;
            }
            case BOOLEAN -> {
                String l = v.toLowerCase(Locale.ROOT);
                if (l.equals("true") || l.equals("sim")) {
                    yield "true";
                }
                if (l.equals("false") || l.equals("nao") || l.equals("não")) {
                    yield "false";
                }
                throw BusinessException.unprocessable("O atributo '" + def.getName() + "' deve ser Sim ou Não");
            }
        };
    }
}
