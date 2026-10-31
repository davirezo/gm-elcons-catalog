package br.com.gmelcons.catalog.dto;

import br.com.gmelcons.catalog.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Todos os objetos de entrada e saída da API, agrupados para facilitar a leitura. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Comuns ----------
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record ApiError(int status, String message, Map<String, String> fieldErrors, Instant timestamp) {
    }

    // ---------- Usuários ----------
    public record MeDto(String username, String fullName, Role role) {
    }

    public record UserDto(Long id, String username, String fullName, Role role, boolean enabled, Instant createdAt) {
    }

    public record UserCreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,50}$", message = "use 3 a 50 caracteres: letras minúsculas, números, ponto, hífen ou sublinhado") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 8, max = 72, message = "a senha deve ter de 8 a 72 caracteres") String password,
            @NotNull Role role) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            boolean enabled,
            @Size(min = 8, max = 72, message = "a senha deve ter de 8 a 72 caracteres") String password) {
    }

    // ---------- Categorias e atributos ----------
    public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {
    }

    public record AttributeDefinitionRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AttributeType dataType,
            boolean required,
            @Size(max = 30) String unit,
            Integer displayOrder) {
    }

    public record AttributeDefinitionDto(Long id, Long categoryId, String name, AttributeType dataType,
                                         boolean required, String unit, int displayOrder) {
    }

    public record CategoryDto(Long id, String name, String description, long productCount,
                              List<AttributeDefinitionDto> attributes) {
    }

    // ---------- Produtos ----------
    public record ProductCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 60) String sku,
            @Size(max = 100) String brand,
            @NotNull Long categoryId,
            @Size(max = 5000) String description) {
    }

    public record ProductUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 60) String sku,
            @Size(max = 100) String brand,
            @Size(max = 5000) String description) {
    }

    public record DuplicateRequest(@NotBlank @Size(max = 60) String sku, @Size(max = 200) String name) {
    }

    public record ApproveRequest(boolean confirmed) {
    }

    public record AttributeValueItem(@NotNull Long definitionId, @Size(max = 1000) String value, Long sourceId,
                                     Boolean reviewed) {
    }

    public record AttributeValuesRequest(@NotNull @Valid List<AttributeValueItem> items) {
    }

    public record ProductSummary(Long id, String name, String sku, String brand, Long categoryId, String categoryName,
                                 ProductStatus status, String createdBy, Instant updatedAt, String duplicatedFromSku) {
    }

    public record AttributeView(Long definitionId, String name, AttributeType dataType, String unit, boolean required,
                                String value, Long sourceId, boolean needsReview) {
    }

    public record ValidationResult(List<String> blockers, List<String> warnings) {
        public boolean ready() {
            return blockers.isEmpty();
        }
    }

    public record ProductDetail(Long id, String name, String sku, String brand, Long categoryId, String categoryName,
                                String description, ProductStatus status, String createdBy, Instant createdAt,
                                Instant updatedAt, Long duplicatedFromId, String duplicatedFromSku,
                                String reviewedBy, Instant reviewedAt, List<AttributeView> attributes,
                                List<SourceDto> sources, ValidationResult validation, boolean hasListing) {
    }

    public record BriefingDto(Long productId, String text) {
    }

    // ---------- Fontes ----------
    public record SourceRequest(
            @NotNull SourceType type,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) @Pattern(regexp = "^$|^https?://\\S+$", message = "a URL deve começar com http:// ou https://") String url,
            LocalDate consultedAt,
            @Size(max = 2000) String notes) {
    }

    public record SourceDto(Long id, Long productId, String productName, String productSku, SourceType type,
                            String name, String url, LocalDate consultedAt, String notes, boolean official) {
    }

    // ---------- Anúncios ----------
    public record ListingRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 10000) String description,
            @Size(max = 1000) String keywords) {
    }

    public record ListingDto(Long id, Long productId, String productName, String productSku, String title,
                             String description, String keywords, Instant preparedAt, Instant updatedAt,
                             String reviewedBy) {
    }

    // ---------- Auditoria e dashboard ----------
    public record AuditDto(Long id, Instant occurredAt, String username, AuditOperation operation, String entityType,
                           Long entityId, String oldValue, String newValue) {
    }

    public record ActivityDto(Instant occurredAt, String username, AuditOperation operation, String entityType,
                              Long entityId) {
    }

    public record DashboardDto(long total, long incompleto, long aguardandoRevisao, long pronto,
                               List<ListingDto> recentListings, List<ActivityDto> recentActivity) {
    }
}
