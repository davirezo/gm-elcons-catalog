package br.com.gmelcons.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Liga o JPA Auditing: as entidades com @LastModifiedBy / @LastModifiedDate recebem usuário e data
 * automaticamente. O auditor é o nome do usuário logado; sem login (ex.: tarefas internas) vale "sistema".
 * Não consulta o banco, para não disparar novo flush dentro do callback do Hibernate.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of("sistema");
            }
            return Optional.of(auth.getName());
        };
    }
}
