package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.dto.Dtos.ActivityDto;
import br.com.gmelcons.catalog.dto.Dtos.DashboardDto;
import br.com.gmelcons.catalog.repository.AdListingRepository;
import br.com.gmelcons.catalog.repository.AuditLogRepository;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ProductRepository products;
    private final AdListingRepository listings;
    private final AuditLogRepository auditLogs;

    public DashboardDto summary() {
        return new DashboardDto(
                products.count(),
                products.countByStatus(ProductStatus.INCOMPLETO),
                products.countByStatus(ProductStatus.AGUARDANDO_REVISAO),
                products.countByStatus(ProductStatus.PRONTO_PARA_ANUNCIO),
                listings.findTop5ByOrderByUpdatedAtDesc().stream().map(ListingService::toDto).toList(),
                // somente resumo da atividade: valores antigos/novos ficam restritos à tela de auditoria (ADMIN)
                auditLogs.findTop10ByOrderByOccurredAtDescIdDesc().stream()
                        .map(a -> new ActivityDto(a.getOccurredAt(), a.getUsername(), a.getOperation(),
                                a.getEntityType(), a.getEntityId()))
                        .toList());
    }
}
