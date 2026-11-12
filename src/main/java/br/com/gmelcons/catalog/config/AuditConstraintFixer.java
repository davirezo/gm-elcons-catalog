package br.com.gmelcons.catalog.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * O Hibernate cria em audit_logs uma restrição CHECK com os valores de AuditOperation que existiam quando a
 * tabela foi criada, e o ddl-auto=update não a atualiza. Sem isto, um banco já existente recusaria operações
 * novas (ex.: IMAGE_ADD, ACCOUNT_LOCKED). O valor continua validado pelo enum Java e pela aplicação.
 * Só atua no PostgreSQL e é idempotente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditConstraintFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditConstraintFixer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())) {
                return;
            }
            jdbc.execute("ALTER TABLE IF EXISTS audit_logs DROP CONSTRAINT IF EXISTS audit_logs_operation_check");
        } catch (Exception e) {
            log.warn("Não foi possível ajustar a restrição de operações da auditoria: {}", e.getMessage());
        }
    }
}
