package br.com.gmelcons.catalog.repository;

import br.com.gmelcons.catalog.entity.AdListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdListingRepository extends JpaRepository<AdListing, Long> {
    Optional<AdListing> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    void deleteByProductId(Long productId);

    @Override
    @EntityGraph(attributePaths = {"product", "reviewedBy"})
    Page<AdListing> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"product", "reviewedBy"})
    List<AdListing> findTop5ByOrderByUpdatedAtDesc();
}
