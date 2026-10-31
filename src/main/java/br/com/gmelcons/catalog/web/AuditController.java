package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.dto.Dtos.AuditDto;
import br.com.gmelcons.catalog.dto.Dtos.PageResponse;
import br.com.gmelcons.catalog.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Auditoria (ADMIN)")
public class AuditController {

    private final AuditService service;

    @GetMapping
    public PageResponse<AuditDto> search(@RequestParam(required = false) String username,
                                         @RequestParam(required = false) AuditOperation operation,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "30") int size) {
        return service.search(username, operation, entityType, page, size);
    }
}
