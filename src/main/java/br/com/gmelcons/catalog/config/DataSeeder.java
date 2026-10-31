package br.com.gmelcons.catalog.config;

import br.com.gmelcons.catalog.domain.AttributeType;
import br.com.gmelcons.catalog.domain.Role;
import br.com.gmelcons.catalog.domain.SourceType;
import br.com.gmelcons.catalog.dto.Dtos.*;
import br.com.gmelcons.catalog.entity.AppUser;
import br.com.gmelcons.catalog.repository.AppUserRepository;
import br.com.gmelcons.catalog.repository.CategoryRepository;
import br.com.gmelcons.catalog.service.CategoryService;
import br.com.gmelcons.catalog.service.ProductService;
import br.com.gmelcons.catalog.service.SourceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Cria o primeiro administrador e, se app.seed.demo-data=true, categorias e produtos FICTÍCIOS.
 * Nenhuma especificação técnica real é cadastrada: valores de exemplo vêm marcados com "[EXEMPLO]".
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    // Credenciais de DEMONSTRAÇÃO (documentadas no README). Nunca use em produção.
    static final String DEMO_ADMIN_PASSWORD = "Admin@Demo2026";
    static final String DEMO_OPERATOR_PASSWORD = "Operador@Demo2026";
    private static final String EXAMPLE = "[EXEMPLO] preencher com dado verificado";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SeedProperties props;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final SourceService sourceService;

    @Override
    public void run(ApplicationArguments args) {
        ensureUsers();
        if (props.demoData() && categoryRepository.count() == 0) {
            AppUser admin = users.findAllByOrderByUsernameAsc().stream()
                    .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled())
                    .findFirst().orElse(null);
            if (admin == null) {
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    admin.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            try {
                seedDemoCatalog();
                log.info("Dados de demonstração (fictícios) carregados.");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private void ensureUsers() {
        if (users.count() > 0) {
            return;
        }
        String adminPassword = props.adminPassword();
        if (adminPassword == null || adminPassword.isBlank()) {
            if (!props.demoData()) {
                throw new IllegalStateException(
                        "Defina APP_ADMIN_PASSWORD para criar o primeiro administrador (APP_SEED_DEMO=false).");
            }
            adminPassword = DEMO_ADMIN_PASSWORD;
        }
        String adminName = (props.adminUsername() == null || props.adminUsername().isBlank())
                ? "admin" : props.adminUsername().trim().toLowerCase(Locale.ROOT);
        createUser(adminName, "Administrador", adminPassword, Role.ADMIN);
        if (props.demoData()) {
            String opPassword = (props.operatorPassword() == null || props.operatorPassword().isBlank())
                    ? DEMO_OPERATOR_PASSWORD : props.operatorPassword();
            createUser("operador", "Operador de demonstração", opPassword, Role.OPERADOR);
        }
    }

    private void createUser(String username, String fullName, String password, Role role) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setEnabled(true);
        users.save(u);
    }

    private void seedDemoCatalog() {
        // Definições de atributos (nomes de campos, não valores). O ADMIN pode ajustá-las na tela de categorias.
        CategoryDto tomadas = categoryService.create(new CategoryRequest("Tomadas", "Tomadas elétricas"));
        categoryService.addAttribute(tomadas.id(), attr("Modelo / referência", AttributeType.TEXT, true, null, 1));
        categoryService.addAttribute(tomadas.id(), attr("Tensão nominal", AttributeType.NUMBER, true, "V", 2));
        categoryService.addAttribute(tomadas.id(), attr("Corrente nominal", AttributeType.NUMBER, true, "A", 3));
        categoryService.addAttribute(tomadas.id(), attr("Cor", AttributeType.TEXT, false, null, 4));

        CategoryDto kits = categoryService.create(new CategoryRequest("Kits de condulete com tomadas",
                "Condulete montado com tomadas"));
        categoryService.addAttribute(kits.id(), attr("Modelo / referência", AttributeType.TEXT, true, null, 1));
        categoryService.addAttribute(kits.id(), attr("Quantidade de tomadas", AttributeType.NUMBER, true, null, 2));
        categoryService.addAttribute(kits.id(), attr("Material", AttributeType.TEXT, false, null, 3));

        CategoryDto interruptores = categoryService.create(new CategoryRequest("Interruptores", "Interruptores elétricos"));
        categoryService.addAttribute(interruptores.id(), attr("Modelo / referência", AttributeType.TEXT, true, null, 1));
        categoryService.addAttribute(interruptores.id(), attr("Número de teclas", AttributeType.NUMBER, true, null, 2));
        categoryService.addAttribute(interruptores.id(), attr("Tensão nominal", AttributeType.NUMBER, false, "V", 3));

        CategoryDto sensores = categoryService.create(new CategoryRequest("Sensores de alarme Margirius",
                "Sensores de alarme da marca Margirius"));
        AttributeDefinitionDto tipo = categoryService.addAttribute(sensores.id(),
                attr("Tipo de sensor", AttributeType.TEXT, true, null, 1));
        AttributeDefinitionDto alimentacao = categoryService.addAttribute(sensores.id(),
                attr("Alimentação", AttributeType.TEXT, true, null, 2));
        categoryService.addAttribute(sensores.id(), attr("Possui proteção antissabotagem", AttributeType.BOOLEAN, false, null, 3));

        // 1) produto incompleto
        productService.create(new ProductCreateRequest("[EXEMPLO] Tomada - pendente de dados reais", "DEMO-TOM-001",
                "[Marca de exemplo]", tomadas.id(), "Produto fictício para demonstração. Preencher com dados verificados."));

        // 2) produto aguardando revisão (valores de exemplo, claramente identificados)
        ProductDetail sensor = productService.create(new ProductCreateRequest("[EXEMPLO] Sensor de alarme Margirius",
                "DEMO-SEN-001", "Margirius", sensores.id(), "Produto fictício para demonstração do fluxo de revisão."));
        SourceDto fonte = sourceService.add(sensor.id(), new SourceRequest(SourceType.FABRICANTE,
                "[EXEMPLO] Catálogo do fabricante (fictício)", null, LocalDate.now(), "Fonte fictícia de demonstração."));
        productService.updateAttributes(sensor.id(), new AttributeValuesRequest(List.of(
                new AttributeValueItem(tipo.id(), EXAMPLE, fonte.id(), true),
                new AttributeValueItem(alimentacao.id(), EXAMPLE, fonte.id(), true))));
        productService.submitReview(sensor.id());

        // 3) duplicação: atributos copiados exigem revisão
        productService.duplicate(sensor.id(), new DuplicateRequest("DEMO-SEN-002",
                "[EXEMPLO] Sensor de alarme Margirius - variação"));

        // 4) produto com apenas uma fonte de concorrente
        ProductDetail interruptor = productService.create(new ProductCreateRequest("[EXEMPLO] Interruptor simples",
                "DEMO-INT-001", "[Marca de exemplo]", interruptores.id(), "Produto fictício para demonstração."));
        sourceService.add(interruptor.id(), new SourceRequest(SourceType.ANUNCIO_CONCORRENTE,
                "[EXEMPLO] Anúncio de concorrente (fictício)", null, LocalDate.now(), "Referência fictícia."));
    }

    private static AttributeDefinitionRequest attr(String name, AttributeType type, boolean required, String unit, int order) {
        return new AttributeDefinitionRequest(name, type, required, unit, order);
    }
}
