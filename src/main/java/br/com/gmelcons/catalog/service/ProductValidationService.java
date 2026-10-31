package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.ValidationResult;
import br.com.gmelcons.catalog.entity.AttributeDefinition;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.entity.ProductAttributeValue;
import br.com.gmelcons.catalog.repository.AttributeDefinitionRepository;
import br.com.gmelcons.catalog.repository.InformationSourceRepository;
import br.com.gmelcons.catalog.repository.ProductAttributeValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras que decidem se um produto pode seguir no fluxo.
 * Bloqueios (blockers) impedem o avanço; avisos (warnings) apenas alertam o revisor.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductValidationService {

    private final AttributeDefinitionRepository definitions;
    private final ProductAttributeValueRepository values;
    private final InformationSourceRepository sources;

    public ValidationResult validate(Product product) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sources.countByProductId(product.getId()) == 0) {
            blockers.add("Cadastre ao menos uma fonte de informação para o produto");
        }

        Map<Long, ProductAttributeValue> byDefinition = new HashMap<>();
        for (ProductAttributeValue v : values.findByProductId(product.getId())) {
            byDefinition.put(v.getDefinition().getId(), v);
        }

        List<AttributeDefinition> defs =
                definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(product.getCategory().getId());
        for (AttributeDefinition def : defs) {
            ProductAttributeValue v = byDefinition.get(def.getId());
            boolean filled = v != null && v.getValue() != null && !v.getValue().isBlank();
            if (!filled) {
                if (def.isRequired()) {
                    blockers.add("Atributo obrigatório não preenchido: " + def.getName());
                }
                continue;
            }
            if (v.isNeedsReview()) {
                blockers.add("Atributo copiado de outro produto e ainda não revisado: " + def.getName());
            }
            if (v.getSource() == null) {
                if (def.isRequired()) {
                    blockers.add("Atributo obrigatório sem fonte de informação: " + def.getName());
                } else {
                    warnings.add("Atributo sem fonte de informação: " + def.getName());
                }
            } else if (v.getSource().getType() == SourceType.ANUNCIO_CONCORRENTE) {
                warnings.add("O atributo '" + def.getName()
                        + "' está baseado em anúncio de concorrente; confirme com fonte oficial (fabricante ou fornecedor)");
            }
        }
        return new ValidationResult(blockers, warnings);
    }
}
