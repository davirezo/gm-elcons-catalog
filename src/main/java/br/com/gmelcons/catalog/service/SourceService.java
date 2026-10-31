package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.PageResponse;
import br.com.gmelcons.catalog.dto.Dtos.SourceDto;
import br.com.gmelcons.catalog.dto.Dtos.SourceRequest;
import br.com.gmelcons.catalog.entity.InformationSource;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.entity.ProductAttributeValue;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.InformationSourceRepository;
import br.com.gmelcons.catalog.repository.ProductAttributeValueRepository;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static br.com.gmelcons.catalog.service.AuditService.fields;
import static br.com.gmelcons.catalog.service.Text.blankToNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceService {

    private final InformationSourceRepository sources;
    private final ProductRepository products;
    private final ProductAttributeValueRepository values;
    private final AuditService audit;
    private final ProductWorkflowService workflow;

    public List<SourceDto> listByProduct(Long productId) {
        if (!products.existsById(productId)) {
            throw new NotFoundException("Produto não encontrado");
        }
        return sources.findByProductIdOrderByIdAsc(productId).stream().map(SourceService::toDto).toList();
    }

    public PageResponse<SourceDto> listAll(SourceType type, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<InformationSource> result = type == null ? sources.findAll(pageable) : sources.findByType(type, pageable);
        return PageResponse.of(result.map(SourceService::toDto));
    }

    @Transactional
    public SourceDto add(Long productId, SourceRequest req) {
        Product product = products.findById(productId).orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        InformationSource source = new InformationSource();
        source.setProduct(product);
        apply(source, req);
        sources.save(source);
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.SOURCE_CREATE, "InformationSource", source.getId(), null, snapshot(source));
        workflow.reevaluateAfterEdit(product);
        return toDto(source);
    }

    @Transactional
    public SourceDto update(Long id, SourceRequest req) {
        InformationSource source = find(id);
        Map<String, Object> before = snapshot(source);
        apply(source, req);
        Product product = source.getProduct();
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.SOURCE_UPDATE, "InformationSource", id, before, snapshot(source));
        workflow.reevaluateAfterEdit(product);
        return toDto(source);
    }

    @Transactional
    public void delete(Long id) {
        InformationSource source = find(id);
        Product product = source.getProduct();
        Map<String, Object> before = snapshot(source);
        for (ProductAttributeValue v : values.findBySourceId(id)) {
            v.setSource(null);
        }
        sources.delete(source);
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.SOURCE_DELETE, "InformationSource", id, before, null);
        workflow.reevaluateAfterEdit(product);
    }

    private InformationSource find(Long id) {
        return sources.findById(id).orElseThrow(() -> new NotFoundException("Fonte não encontrada"));
    }

    private void apply(InformationSource source, SourceRequest req) {
        source.setType(req.type());
        source.setName(req.name().trim());
        source.setUrl(blankToNull(req.url()));
        source.setConsultedAt(req.consultedAt());
        source.setNotes(blankToNull(req.notes()));
    }

    private static Map<String, Object> snapshot(InformationSource s) {
        return fields("product", s.getProduct().getSku(), "type", s.getType(), "name", s.getName(), "url", s.getUrl(),
                "consultedAt", s.getConsultedAt(), "notes", s.getNotes());
    }

    public static SourceDto toDto(InformationSource s) {
        return new SourceDto(s.getId(), s.getProduct().getId(), s.getProduct().getName(), s.getProduct().getSku(),
                s.getType(), s.getName(), s.getUrl(), s.getConsultedAt(), s.getNotes(),
                s.getType() == SourceType.FABRICANTE);
    }
}
