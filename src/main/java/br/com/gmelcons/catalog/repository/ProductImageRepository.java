package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Projeção só com os metadados: não carrega os bytes da imagem. */
    interface Meta {
        Long getId();

        String getFileName();

        String getContentType();

        long getSizeBytes();

        Instant getCreatedAt();
    }

    interface IdRow {
        Long getProductId();

        Long getId();
    }

    List<Meta> findByProductIdOrderByPositionAscIdAsc(Long productId);

    long countByProductId(Long productId);

    @Query("select coalesce(max(i.position), -1) from ProductImage i where i.product.id = :productId")
    int maxPosition(@Param("productId") Long productId);

    @Query("select coalesce(min(i.position), 1) from ProductImage i where i.product.id = :productId")
    int minPosition(@Param("productId") Long productId);

    /** Ids das imagens dos produtos informados, em ordem; a primeira de cada produto é a principal. */
    @Query("select i.product.id as productId, i.id as id from ProductImage i "
            + "where i.product.id in :productIds order by i.position asc, i.id asc")
    List<IdRow> findOrderedIds(@Param("productIds") Collection<Long> productIds);

    @Modifying
    @Query("delete from ProductImage i where i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
