package br.com.gmelcons.catalog.entity;

import br.com.gmelcons.catalog.domain.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "information_sources")
@Getter
@Setter
@NoArgsConstructor
public class InformationSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String url;

    private LocalDate consultedAt;

    @Column(length = 2000)
    private String notes;
}
