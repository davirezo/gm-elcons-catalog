package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {
    List<AttributeDefinition> findByCategoryIdOrderByDisplayOrderAscIdAsc(Long categoryId);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);

    long countByCategoryId(Long categoryId);
}
