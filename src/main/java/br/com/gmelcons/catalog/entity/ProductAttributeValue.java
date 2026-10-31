package br.com.gmelcons.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "definition_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id")
    private AttributeDefinition definition;

    @Column(name = "attr_value", length = 1000)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private InformationSource source;

    /** true quando o valor foi copiado de outro produto e ainda não foi revisado. */
    @Column(nullable = false)
    private boolean needsReview;
}
