package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.dto.Dtos.AuditDto;
import br.com.gmelcons.catalog.dto.Dtos.PageResponse;
import br.com.gmelcons.catalog.entity.AuditLog;
import br.com.gmelcons.catalog.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de operações críticas. Participa da transação de quem o chama:
 * se a operação falhar e der rollback, o registro correspondente também não é gravado.
 * Limitação documentada: quem tem acesso direto ao banco pode alterar esses registros.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;

    /** Monta um mapa ordenado que aceita valores nulos: fields("nome", x, "sku", y). */
    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @Transactional
    public void log(AuditOperation operation, String entityType, Long entityId, Object oldValue, Object newValue) {
        logAs(currentUser.username(), operation, entityType, entityId, oldValue, newValue);
    }

    @Transactional
    public void logAs(String username, AuditOperation operation, String entityType, Long entityId,
                      Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog();
        entry.setUsername(username == null ? "desconhecido" : truncate(username, 100));
        entry.setOperation(operation);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        repository.save(entry);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<AuditDto> search(String username, AuditOperation operation, String entityType,
                                         int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.trim().toLowerCase() + "%"));
            }
            if (operation != null) {
                predicates.add(cb.equal(root.get("operation"), operation));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return PageResponse.of(repository.findAll(spec, pageable).map(this::toDto));
    }

    private AuditDto toDto(AuditLog a) {
        return new AuditDto(a.getId(), a.getOccurredAt(), a.getUsername(), a.getOperation(), a.getEntityType(),
                a.getEntityId(), a.getOldValue(), a.getNewValue());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
