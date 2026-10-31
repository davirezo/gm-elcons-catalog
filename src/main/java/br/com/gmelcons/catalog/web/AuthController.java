package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.dto.Dtos.MeDto;
import br.com.gmelcons.catalog.entity.AppUser;
import br.com.gmelcons.catalog.service.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação")
public class AuthController {

    private final CurrentUser currentUser;

    @GetMapping("/me")
    public MeDto me() {
        AppUser u = currentUser.entity();
        return new MeDto(u.getUsername(), u.getFullName(), u.getRole());
    }
}
