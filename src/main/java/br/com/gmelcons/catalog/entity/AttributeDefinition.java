package br.com.gmelcons.catalog.entity;

import br.com.gmelcons.catalog.domain.AttributeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attribute_definitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class AttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType dataType;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(length = 30)
    private String unit;

    @Column(nullable = false)
    private int displayOrder;
}
