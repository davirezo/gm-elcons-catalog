package br.com.gmelcons.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("GM Elcons Catalog API")
                .version("0.1.0")
                .description("Cadastro técnico de produtos, fontes de informação, briefing e anúncios preparados. "
                        + "A API usa sessão com cookie: faça login na aplicação antes de abrir esta página. "
                        + "Requisições que alteram dados exigem o cabeçalho X-XSRF-TOKEN (valor do cookie XSRF-TOKEN)."));
    }
}
