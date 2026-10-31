package br.com.gmelcons.catalog;

import br.com.gmelcons.catalog.domain.AttributeType;
import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.ValidationResult;
import br.com.gmelcons.catalog.entity.*;
import br.com.gmelcons.catalog.repository.AttributeDefinitionRepository;
import br.com.gmelcons.catalog.repository.InformationSourceRepository;
import br.com.gmelcons.catalog.repository.ProductAttributeValueRepository;
import br.com.gmelcons.catalog.service.ProductValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Teste unitário (Mockito) das regras de validação, sem banco de dados. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductValidationService (unitário)")
class ProductValidationServiceTest {

    @Mock AttributeDefinitionRepository definitions;
    @Mock ProductAttributeValueRepository values;
    @Mock InformationSourceRepository sources;
    @InjectMocks ProductValidationService service;

    private Product product;
    private AttributeDefinition required;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setId(5L);
        product = new Product();
        product.setId(1L);
        product.setCategory(category);

        required = new AttributeDefinition();
        required.setId(10L);
        required.setName("Tensão nominal");
        required.setDataType(AttributeType.NUMBER);
        required.setRequired(true);
        required.setCategory(category);
        when(definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(5L)).thenReturn(List.of(required));
    }

    private InformationSource source(SourceType type) {
        InformationSource s = new InformationSource();
        s.setId(20L);
        s.setType(type);
        s.setName("Fonte");
        return s;
    }

    private ProductAttributeValue value(String v, InformationSource src, boolean needsReview) {
        ProductAttributeValue pv = new ProductAttributeValue();
        pv.setDefinition(required);
        pv.setProduct(product);
        pv.setValue(v);
        pv.setSource(src);
        pv.setNeedsReview(needsReview);
        return pv;
    }

    @Test
    @DisplayName("sem fonte e sem atributo obrigatório: dois bloqueios")
    void missingSourceAndRequiredAttribute() {
        when(sources.countByProductId(1L)).thenReturn(0L);
        when(values.findByProductId(1L)).thenReturn(List.of());

        ValidationResult r = service.validate(product);

        assertThat(r.ready()).isFalse();
        assertThat(r.blockers()).hasSize(2);
        assertThat(String.join(" ", r.blockers())).contains("fonte").contains("Tensão nominal");
    }

    @Test
    @DisplayName("valor em branco conta como não preenchido")
    void blankValueIsMissing() {
        when(sources.countByProductId(1L)).thenReturn(1L);
        when(values.findByProductId(1L)).thenReturn(List.of(value("   ", null, false)));

        assertThat(service.validate(product).blockers()).anyMatch(b -> b.contains("obrigatório não preenchido"));
    }

    @Test
    @DisplayName("valor copiado e não revisado bloqueia")
    void copiedValueNeedsReview() {
        when(sources.countByProductId(1L)).thenReturn(1L);
        when(values.findByProductId(1L)).thenReturn(List.of(value("127", source(SourceType.FABRICANTE), true)));

        ValidationResult r = service.validate(product);

        assertThat(r.ready()).isFalse();
        assertThat(r.blockers()).anyMatch(b -> b.contains("não revisado"));
    }

    @Test
    @DisplayName("atributo obrigatório sem fonte bloqueia")
    void requiredAttributeWithoutSource() {
        when(sources.countByProductId(1L)).thenReturn(1L);
        when(values.findByProductId(1L)).thenReturn(List.of(value("127", null, false)));

        assertThat(service.validate(product).blockers()).anyMatch(b -> b.contains("sem fonte"));
    }

    @Test
    @DisplayName("fonte de concorrente não bloqueia, mas gera aviso")
    void competitorSourceOnlyWarns() {
        when(sources.countByProductId(1L)).thenReturn(1L);
        when(values.findByProductId(1L)).thenReturn(List.of(value("127", source(SourceType.ANUNCIO_CONCORRENTE), false)));

        ValidationResult r = service.validate(product);

        assertThat(r.ready()).isTrue();
        assertThat(r.warnings()).anyMatch(w -> w.contains("concorrente"));
    }

    @Test
    @DisplayName("dados completos, revisados e com fonte oficial: sem bloqueios nem avisos")
    void everythingOk() {
        when(sources.countByProductId(1L)).thenReturn(1L);
        when(values.findByProductId(1L)).thenReturn(List.of(value("127", source(SourceType.FABRICANTE), false)));

        ValidationResult r = service.validate(product);

        assertThat(r.ready()).isTrue();
        assertThat(r.warnings()).isEmpty();
    }
}
