package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AttributeType;
import br.com.gmelcons.catalog.domain.ProductStatus;
import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.BriefingDto;
import br.com.gmelcons.catalog.entity.AttributeDefinition;
import br.com.gmelcons.catalog.entity.InformationSource;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.entity.ProductAttributeValue;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.AttributeDefinitionRepository;
import br.com.gmelcons.catalog.repository.InformationSourceRepository;
import br.com.gmelcons.catalog.repository.ProductAttributeValueRepository;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerador de briefing: monta um texto organizado, com os dados técnicos revisados do produto,
 * para o usuário copiar e usar no ChatGPT. Não há integração com IA nesta versão.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BriefingService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOMENT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final ProductRepository products;
    private final AttributeDefinitionRepository definitions;
    private final ProductAttributeValueRepository values;
    private final InformationSourceRepository sources;

    public BriefingDto generate(Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        if (p.getStatus() != ProductStatus.PRONTO_PARA_ANUNCIO) {
            throw BusinessException.conflict(
                    "O briefing só pode ser gerado para produtos com status PRONTO_PARA_ANUNCIO (dados revisados)");
        }

        Map<Long, ProductAttributeValue> byDefinition = new HashMap<>();
        for (ProductAttributeValue v : values.findByProductId(productId)) {
            byDefinition.put(v.getDefinition().getId(), v);
        }
        List<String> missing = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("BRIEFING PARA CRIAÇÃO DE ANÚNCIO\n");
        sb.append("================================\n\n");
        sb.append("Produto: ").append(p.getName()).append('\n');
        sb.append("Marca: ").append(p.getBrand() == null ? "não informada" : p.getBrand()).append('\n');
        sb.append("Categoria: ").append(p.getCategory().getName()).append('\n');
        sb.append("Código interno (SKU): ").append(p.getSku()).append("\n\n");

        sb.append("CARACTERÍSTICAS TÉCNICAS (revisadas)\n");
        sb.append("------------------------------------\n");
        for (AttributeDefinition d : definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(p.getCategory().getId())) {
            ProductAttributeValue v = byDefinition.get(d.getId());
            if (v == null || v.getValue() == null || v.getValue().isBlank()) {
                missing.add(d.getName());
                continue;
            }
            sb.append("- ").append(d.getName()).append(": ").append(display(d, v.getValue()));
            if (v.getSource() != null) {
                sb.append("  [fonte: ").append(label(v.getSource().getType())).append(" - ")
                        .append(v.getSource().getName()).append(']');
            }
            sb.append('\n');
        }
        if (!missing.isEmpty()) {
            sb.append("\nInformações NÃO disponíveis (não invente; se forem necessárias, sinalize como pendentes): ")
                    .append(String.join(", ", missing)).append('\n');
        }

        sb.append("\nFONTES CONSULTADAS\n");
        sb.append("------------------\n");
        int n = 1;
        for (InformationSource s : sources.findByProductIdOrderByIdAsc(productId)) {
            sb.append(n++).append(". [").append(label(s.getType())).append("] ").append(s.getName());
            if (s.getUrl() != null) {
                sb.append(" - ").append(s.getUrl());
            }
            if (s.getConsultedAt() != null) {
                sb.append(" (consultada em ").append(s.getConsultedAt().format(DAY)).append(')');
            }
            if (s.getNotes() != null) {
                sb.append(" - obs.: ").append(s.getNotes());
            }
            sb.append('\n');
        }

        sb.append("\nOBSERVAÇÕES\n");
        sb.append("-----------\n");
        sb.append(p.getDescription() == null ? "Sem observações gerais." : p.getDescription()).append('\n');
        if (p.getReviewedBy() != null && p.getReviewedAt() != null) {
            sb.append("Dados revisados por ").append(p.getReviewedBy().getUsername())
                    .append(" em ").append(MOMENT.format(p.getReviewedAt())).append(".\n");
        }

        sb.append("\nORIENTAÇÕES PARA O ASSISTENTE\n");
        sb.append("-----------------------------\n");
        sb.append("Com base SOMENTE nas informações acima, produza:\n");
        sb.append("1. Três opções de TÍTULO para o anúncio, começando pelo tipo de produto e incluindo marca e as\n");
        sb.append("   características mais relevantes para a busca do comprador.\n");
        sb.append("2. Uma DESCRIÇÃO clara e organizada em tópicos, em português do Brasil, sem exageros de marketing.\n");
        sb.append("3. Uma lista de PALAVRAS-CHAVE relevantes, separadas por vírgula.\n");
        sb.append("Regras: não invente medidas, certificações, compatibilidades ou garantias; se algo necessário\n");
        sb.append("estiver ausente, liste como \"informação pendente\". Respeite as regras de conteúdo vigentes do\n");
        sb.append("Mercado Livre para títulos e descrições.\n");

        return new BriefingDto(productId, sb.toString());
    }

    private static String display(AttributeDefinition d, String value) {
        if (d.getDataType() == AttributeType.BOOLEAN) {
            return "true".equals(value) ? "Sim" : "Não";
        }
        return d.getUnit() == null ? value : value + " " + d.getUnit();
    }

    private static String label(SourceType type) {
        return switch (type) {
            case FABRICANTE -> "Fabricante";
            case FORNECEDOR -> "Fornecedor";
            case ANUNCIO_CONCORRENTE -> "Anúncio de concorrente";
            case OUTRA -> "Outra referência";
        };
    }
}
