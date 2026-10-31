package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.dto.Dtos.ValidationResult;
import br.com.gmelcons.catalog.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static br.com.gmelcons.catalog.service.AuditService.fields;

/** Transições de status do produto. Toda mudança é auditada. */
@Service
@RequiredArgsConstructor
public class ProductWorkflowService {

    private final ProductValidationService validation;
    private final AuditService audit;

    /**
     * Chamado após qualquer edição: se surgiram pendências o produto volta a INCOMPLETO;
     * se estava PRONTO e foi alterado, volta a AGUARDANDO_REVISAO para nova revisão.
     */
    @Transactional
    public void reevaluateAfterEdit(Product product) {
        ValidationResult result = validation.validate(product);
        ProductStatus current = product.getStatus();
        ProductStatus next = current;
        if (!result.blockers().isEmpty()) {
            next = ProductStatus.INCOMPLETO;
        } else if (current == ProductStatus.PRONTO_PARA_ANUNCIO) {
            next = ProductStatus.AGUARDANDO_REVISAO;
        }
        if (next != current) {
            changeStatus(product, next, "Reavaliação automática após alteração");
        }
    }

    @Transactional
    public void changeStatus(Product product, ProductStatus next, String reason) {
        ProductStatus previous = product.getStatus();
        product.setStatus(next);
        if (next != ProductStatus.PRONTO_PARA_ANUNCIO) {
            product.setReviewedBy(null);
            product.setReviewedAt(null);
        }
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.STATUS_CHANGE, "Product", product.getId(),
                fields("sku", product.getSku(), "status", previous),
                fields("sku", product.getSku(), "status", next, "reason", reason));
    }
}
