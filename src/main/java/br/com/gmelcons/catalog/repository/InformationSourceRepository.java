package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.entity.InformationSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InformationSourceRepository extends JpaRepository<InformationSource, Long> {
    List<InformationSource> findByProductIdOrderByIdAsc(Long productId);

    long countByProductId(Long productId);

    void deleteByProductId(Long productId);

    @Override
    @EntityGraph(attributePaths = "product")
    Page<InformationSource> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<InformationSource> findByType(SourceType type, Pageable pageable);
}
