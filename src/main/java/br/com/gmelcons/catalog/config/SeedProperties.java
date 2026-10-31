package br.com.gmelcons.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades app.seed.*: criação do primeiro administrador e dos dados de demonstração. */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean demoData, String adminUsername, String adminPassword, String operatorPassword) {
}
