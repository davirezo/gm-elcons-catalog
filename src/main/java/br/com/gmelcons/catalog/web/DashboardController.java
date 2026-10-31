package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.dto.Dtos.DashboardDto;
import br.com.gmelcons.catalog.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    public DashboardDto summary() {
        return service.summary();
    }
}
