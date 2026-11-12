package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.dto.Dtos.ProductImageDto;
import br.com.gmelcons.catalog.entity.Product;
import br.com.gmelcons.catalog.entity.ProductImage;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.ProductImageRepository;
import br.com.gmelcons.catalog.repository.ProductImageRepository.Meta;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static br.com.gmelcons.catalog.service.AuditService.fields;

/**
 * Imagens do produto. Regras de segurança do envio: tamanho máximo, quantidade máxima, formato conferido
 * pelo conteúdo (não pelo nome nem pelo Content-Type), limite de dimensões e nome só para exibição.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductImageService {

    public static final int MAX_PER_PRODUCT = 6;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_SIDE = 8000;
    static final long MAX_PIXELS = 40_000_000L;

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public record ImageContent(String contentType, byte[] data) {
    }

    public List<ProductImageDto> list(Long productId) {
        if (!products.existsById(productId)) {
            throw new NotFoundException("Produto não encontrado");
        }
        List<Meta> metas = images.findByProductIdOrderByPositionAscIdAsc(productId);
        List<ProductImageDto> out = new ArrayList<>();
        for (int i = 0; i < metas.size(); i++) {
            Meta m = metas.get(i);
            out.add(new ProductImageDto(m.getId(), productId, m.getFileName(), m.getContentType(), m.getSizeBytes(),
                    i == 0, m.getCreatedAt()));
        }
        return out;
    }

    public ImageContent content(Long imageId) {
        ProductImage img = find(imageId);
        return new ImageContent(img.getContentType(), img.getData());
    }

    @Transactional
    public ProductImageDto add(Long productId, MultipartFile file) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        if (file == null || file.isEmpty()) {
            throw BusinessException.unprocessable("Selecione um arquivo de imagem");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BusinessException.payloadTooLarge("A imagem excede o limite de 5 MB");
        }
        long existing = images.countByProductId(productId);
        if (existing >= MAX_PER_PRODUCT) {
            throw BusinessException.conflict("Limite de " + MAX_PER_PRODUCT
                    + " imagens por produto. Remova uma para enviar outra.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.unprocessable("Não foi possível ler o arquivo enviado");
        }
        ImageSniffer.Kind kind = ImageSniffer.detect(bytes);
        if (kind == null) {
            throw BusinessException.unprocessable("Formato não permitido. Envie uma imagem JPG, PNG ou WebP.");
        }
        if (kind != ImageSniffer.Kind.WEBP) {
            int[] dim = ImageSniffer.dimensions(bytes);
            if (dim == null) {
                throw BusinessException.unprocessable("O arquivo não é uma imagem válida");
            }
            if (dim[0] > MAX_SIDE || dim[1] > MAX_SIDE || (long) dim[0] * dim[1] > MAX_PIXELS) {
                throw BusinessException.unprocessable("Imagem muito grande: máximo de " + MAX_SIDE + " x " + MAX_SIDE + " pixels");
            }
        }

        ProductImage img = new ProductImage();
        img.setProduct(product);
        img.setContentType(kind.contentType);
        img.setFileName(cleanName(file.getOriginalFilename(), kind));
        img.setSizeBytes(bytes.length);
        img.setPosition(images.maxPosition(productId) + 1);
        img.setCreatedBy(currentUser.username());
        img.setData(bytes);
        images.save(img);

        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.IMAGE_ADD, "Product", productId, null,
                fields("arquivo", img.getFileName(), "tipo", kind.contentType, "bytes", bytes.length));
        return new ProductImageDto(img.getId(), productId, img.getFileName(), img.getContentType(), img.getSizeBytes(),
                existing == 0, img.getCreatedAt());
    }

    @Transactional
    public void delete(Long imageId) {
        ProductImage img = find(imageId);
        Product product = img.getProduct();
        String name = img.getFileName();
        images.delete(img);
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.IMAGE_REMOVE, "Product", product.getId(), fields("arquivo", name), null);
    }

    @Transactional
    public List<ProductImageDto> setMain(Long imageId) {
        ProductImage img = find(imageId);
        Product product = img.getProduct();
        Long productId = product.getId();
        List<Meta> current = images.findByProductIdOrderByPositionAscIdAsc(productId);
        if (!current.isEmpty() && current.get(0).getId().equals(imageId)) {
            return list(productId);
        }
        img.setPosition(images.minPosition(productId) - 1);
        product.setUpdatedAt(Instant.now());
        audit.log(AuditOperation.IMAGE_SET_MAIN, "Product", productId, null, fields("arquivo", img.getFileName()));
        return list(productId);
    }

    private ProductImage find(Long imageId) {
        return images.findById(imageId).orElseThrow(() -> new NotFoundException("Imagem não encontrada"));
    }

    /** Nome só para exibição: sem caminho e sem caracteres estranhos. */
    static String cleanName(String original, ImageSniffer.Kind kind) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (n.isBlank() || n.chars().allMatch(c -> c == '.' || c == '_')) {
            n = "imagem." + kind.extension;
        }
        return n.length() > 100 ? n.substring(n.length() - 100) : n;
    }
}
