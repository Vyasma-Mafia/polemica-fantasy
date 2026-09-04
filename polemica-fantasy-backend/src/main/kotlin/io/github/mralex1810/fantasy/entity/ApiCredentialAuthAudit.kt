package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "api_credential_auth_audit")
class ApiCredentialAuthAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_credential_id", nullable = false)
    var apiCredential: ApiCredential? = null,

    @Column(nullable = false, length = 16)
    var outcome: String = "",

    @Column(name = "request_method", nullable = false, length = 16)
    var requestMethod: String = "",

    @Column(name = "request_path", nullable = false, length = 512)
    var requestPath: String = "",

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
)
