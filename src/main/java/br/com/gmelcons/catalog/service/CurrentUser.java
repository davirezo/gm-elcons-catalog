package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.entity.AppUser;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acesso ao usuário autenticado na requisição atual. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final AppUserRepository users;

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "sistema";
        }
        return auth.getName();
    }

    public AppUser entity() {
        return users.findByUsername(username())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado"));
    }
}
