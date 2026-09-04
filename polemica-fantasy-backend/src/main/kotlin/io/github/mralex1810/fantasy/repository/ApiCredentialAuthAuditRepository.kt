package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ApiCredentialAuthAudit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApiCredentialAuthAuditRepository : JpaRepository<ApiCredentialAuthAudit, Long> {
    @Modifying
    @Query(
        value =
            """
            DELETE FROM api_credential_auth_audit
            WHERE api_credential_id = :credentialId
              AND id NOT IN (
                  SELECT id
                  FROM api_credential_auth_audit
                  WHERE api_credential_id = :credentialId
                  ORDER BY occurred_at DESC, id DESC
                  LIMIT :keepCount
              )
            """,
        nativeQuery = true,
    )
    fun trimToMostRecent(
        @Param("credentialId") credentialId: Long,
        @Param("keepCount") keepCount: Int,
    ): Int
}
