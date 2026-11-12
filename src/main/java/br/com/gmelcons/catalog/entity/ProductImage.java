package br.com.gmelcons.catalog.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Imagem de um produto, guardada no próprio banco (bytea no PostgreSQL), o que a inclui no backup.
 * A imagem principal é a de menor "position". O nome do arquivo serve só para exibição:
 * nunca é usado como caminho em disco.
 */
@Entity
@Table(name = "product_images", indexes = @Index(name = "idx_product_images_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Tipo detectado pelo conteúdo do arquivo (image/jpeg, image/png ou image/webp), nunca o informado pelo cliente. */
    @Column(nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false, length = 120)
    private String fileName;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 100, updatable = false)
    private String createdBy;

    /**
     * bytea no PostgreSQL (e no H2 em modo PostgreSQL, usado nos testes). Declarado explicitamente porque,
     * com um limite de tamanho grande, o Hibernate trocaria o tipo por "blob" no H2. O limite de 5 MB
     * é aplicado pelo serviço, não pelo tipo da coluna.
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
