package br.com.gmelcons.catalog.entity;

import br.com.gmelcons.catalog.domain.AuditOperation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, updatable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private AuditOperation operation;

    @Column(nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(updatable = false)
    private Long entityId;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String oldValue;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String newValue;
}
