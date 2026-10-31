package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    long countByStatus(ProductStatus status);

    long countByCategoryId(Long categoryId);

    @Override
    @EntityGraph(attributePaths = {"category", "createdBy"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}
