package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.dto.Dtos.ListingDto;
import br.com.gmelcons.catalog.dto.Dtos.ListingRequest;
import br.com.gmelcons.catalog.dto.Dtos.PageResponse;
import br.com.gmelcons.catalog.entity.AdListing;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.AdListingRepository;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static br.com.gmelcons.catalog.service.AuditService.fields;
import static br.com.gmelcons.catalog.service.Text.blankToNull;

/** Armazena o resultado final (título, descrição, palavras-chave) produzido a partir do briefing. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

    private final AdListingRepository listings;
    private final ProductRepository products;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ListingDto getByProduct(Long productId) {
        return listings.findByProductId(productId).map(ListingService::toDto)
                .orElseThrow(() -> new NotFoundException("Este produto ainda não possui anúncio preparado"));
    }

    public PageResponse<ListingDto> list(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.of(listings.findAll(pageable).map(ListingService::toDto));
    }

    @Transactional
    public ListingDto save(Long productId, ListingRequest req) {
        Product product = products.findById(productId).orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        if (product.getStatus() != ProductStatus.PRONTO_PARA_ANUNCIO) {
            throw BusinessException.conflict("O anúncio só pode ser preparado para produtos com status PRONTO_PARA_ANUNCIO");
        }
        Optional<AdListing> found = listings.findByProductId(productId);
        boolean creating = found.isEmpty();
        AdListing listing = found.orElseGet(() -> {
            AdListing l = new AdListing();
            l.setProduct(product);
            l.setPreparedAt(Instant.now());
            return l;
        });
        Map<String, Object> before = creating ? null : snapshot(listing);
        listing.setTitle(req.title().trim());
        listing.setDescription(req.description().trim());
        listing.setKeywords(blankToNull(req.keywords()));
        listing.setUpdatedAt(Instant.now());
        listing.setReviewedBy(currentUser.entity());
        listings.save(listing);
        audit.log(creating ? AuditOperation.LISTING_CREATE : AuditOperation.LISTING_UPDATE, "AdListing",
                listing.getId(), before, snapshot(listing));
        return toDto(listing);
    }

    private static Map<String, Object> snapshot(AdListing l) {
        return fields("sku", l.getProduct().getSku(), "title", l.getTitle(), "description", l.getDescription(),
                "keywords", l.getKeywords());
    }

    public static ListingDto toDto(AdListing l) {
        return new ListingDto(l.getId(), l.getProduct().getId(), l.getProduct().getName(), l.getProduct().getSku(),
                l.getTitle(), l.getDescription(), l.getKeywords(), l.getPreparedAt(), l.getUpdatedAt(),
                l.getReviewedBy().getUsername());
    }
}
