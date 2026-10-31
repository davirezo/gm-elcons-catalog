package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {
    List<ProductAttributeValue> findByProductId(Long productId);

    List<ProductAttributeValue> findBySourceId(Long sourceId);

    long countByDefinitionId(Long definitionId);

    void deleteByProductId(Long productId);
}
