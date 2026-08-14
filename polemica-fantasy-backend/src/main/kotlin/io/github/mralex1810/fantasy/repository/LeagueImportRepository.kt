package io.github.mralex1810.fantasy.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class LeagueImportItemRow(
    val id: Long,
    val sourceChannelId: Long,
    val sourceMessageId: Long,
    val currentRevision: Int,
    val currentSourceVersion: String,
    val currentContentHash: String,
    val currentEvidenceHash: String,
    val classification: String,
    val leagueCode: String,
    val state: String,
    val version: Long,
    val draftJson: JsonNode?,
    val draftChecksum: String?,
    val targetSeriesId: Long?,
    val readinessStatus: String?,
    val readinessChecksum: String?,
    val stablePollCount: Int,
    val readySince: Instant?,
    val lastStableObservationAt: Instant?,
    val policyGeneration: String?,
    val rosterStatus: String,
    val rosterDraftJson: JsonNode?,
    val rosterChecksum: String?,
)

data class LeagueImportRevisionRow(
    val revision: Int,
    val postedAt: Instant,
    val editedAt: Instant?,
    val contentHash: String,
    val evidenceHash: String,
    val rawText: String,
    val mediaEvidence: JsonNode?,
    val observedAt: Instant,
)

data class LeagueImportJobRow(
    val id: UUID,
    val itemId: Long,
    val itemVersion: Long,
    val sourceRevision: Int,
    val evidenceChecksum: String,
    val policyGeneration: String,
    val operation: String,
    val status: String,
    val targetSeriesId: Long?,
    val readinessChecksum: String?,
    val actorTelegramId: Long?,
    val attempts: Int,
    val leaseToken: UUID?,
    val availableAt: Instant?,
    val pendingNotificationOutboxId: Long?,
    val notificationDeliveredAt: Instant?,
    val rosterChecksum: String?,
)

data class LeagueImportActionRow(
    val id: UUID,
    val itemId: Long,
    val itemVersion: Long,
    val sourceRevision: Int,
    val draftChecksum: String,
    val policyGeneration: String,
    val actionType: String,
    val status: String,
    val tokenHash: String,
    val boundActorTelegramId: Long?,
    val actorTelegramId: Long?,
    val operatorChatId: Long,
    val operatorMessageId: Long?,
    val expiresAt: Instant,
    val rosterChecksum: String?,
)

data class LeagueImportOutboxRow(
    val id: Long,
    val eventKey: String,
    val itemId: Long,
    val actionId: UUID?,
    val eventType: String,
    val payload: JsonNode,
)

data class LeagueImportCallbackInboxRow(
    val id: Long,
    val updateId: Long,
    val queryId: String,
    val actionId: UUID?,
    val tokenHash: String,
    val operatorChatId: Long?,
    val operatorMessageId: Long?,
    val actorTelegramId: Long?,
    val actorUsername: String?,
    val actorIsBot: Boolean,
    val ordinaryMessage: Boolean,
)

@Repository
class LeagueImportRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun insertDelivery(keyId: String, deliveryId: UUID, timestamp: Instant, contentHash: String): Boolean =
        jdbc.update(
            """
            INSERT INTO league_import_delivery(key_id,delivery_id,request_timestamp,content_hash)
            VALUES(?,?,?,?) ON CONFLICT(delivery_id) DO NOTHING
            """.trimIndent(),
            keyId, deliveryId, OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC), contentHash,
        ) == 1

    fun findDeliveryContentHash(deliveryId: UUID): String? = jdbc.query(
        "SELECT content_hash FROM league_import_delivery WHERE delivery_id=?",
        { rs, _ -> rs.getString(1) },
        deliveryId,
    ).firstOrNull()

    fun attachDelivery(deliveryId: UUID, itemId: Long) {
        jdbc.update("UPDATE league_import_delivery SET item_id=? WHERE delivery_id=?", itemId, deliveryId)
    }

    fun findItem(sourceChannelId: Long, sourceMessageId: Long, lock: Boolean = false): LeagueImportItemRow? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return jdbc.query(
            "SELECT * FROM league_import_item WHERE source_channel_id=? AND source_message_id=? AND item_index=0$suffix",
            { rs, _ -> item(rs) }, sourceChannelId, sourceMessageId,
        ).firstOrNull()
    }

    fun findItemById(id: Long, lock: Boolean = false): LeagueImportItemRow? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return jdbc.query("SELECT * FROM league_import_item WHERE id=?$suffix", { rs, _ -> item(rs) }, id).firstOrNull()
    }

    fun createItem(
        sourceChannelId: Long,
        sourceMessageId: Long,
        revision: Int,
        sourceVersion: String,
        contentHash: String,
        classification: String,
        league: String,
        evidenceHash: String = contentHash,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO league_import_item(
            source_channel_id,source_message_id,current_revision,current_source_version,
            current_content_hash,current_evidence_hash,classification,league_code,state
        ) VALUES(?,?,?,?,?,?,?,?,'BLOCKED') RETURNING id
        """.trimIndent(),
        Long::class.java,
        sourceChannelId, sourceMessageId, revision, sourceVersion, contentHash, evidenceHash, classification, league,
    )!!

    fun insertRevision(
        itemId: Long,
        sourceChannelId: Long,
        sourceMessageId: Long,
        revision: Int,
        sourceVersion: String,
        postedAt: Instant,
        editedAt: Instant?,
        contentHash: String,
        rawText: String,
        current: Boolean,
        evidenceHash: String = contentHash,
        mediaEvidence: Any? = null,
    ): Boolean = jdbc.update(
        """
        INSERT INTO league_import_revision(
            item_id,source_channel_id,source_message_id,revision,source_version,posted_at,edited_at,
            content_hash,evidence_hash,raw_text,media_evidence,is_current
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
        ON CONFLICT (source_channel_id,source_message_id,revision,evidence_hash) DO UPDATE SET
            source_version=EXCLUDED.source_version,posted_at=EXCLUDED.posted_at,edited_at=EXCLUDED.edited_at,
            raw_text=EXCLUDED.raw_text,media_evidence=EXCLUDED.media_evidence,is_current=EXCLUDED.is_current
        """.trimIndent(),
        itemId, sourceChannelId, sourceMessageId, revision, sourceVersion,
        OffsetDateTime.ofInstant(postedAt, ZoneOffset.UTC), editedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        contentHash, evidenceHash, rawText, mediaEvidence?.let { objectMapper.writeValueAsString(it) }, current,
    ) == 1

    fun markRevisionsNotCurrent(itemId: Long) {
        jdbc.update("UPDATE league_import_revision SET is_current=FALSE WHERE item_id=?", itemId)
    }

    fun advanceCurrentSourceVersion(itemId: Long, sourceVersion: String) {
        jdbc.update(
            "UPDATE league_import_item SET current_source_version=?,updated_at=now() WHERE id=? AND current_source_version<?",
            sourceVersion, itemId, sourceVersion,
        )
    }

    fun currentRevision(itemId: Long): LeagueImportRevisionRow? = jdbc.query(
        "SELECT * FROM league_import_revision WHERE item_id=? AND is_current=TRUE",
        { rs, _ -> revision(rs) }, itemId,
    ).firstOrNull()

    fun updateCurrentItem(
        itemId: Long,
        revision: Int,
        sourceVersion: String,
        contentHash: String,
        classification: String,
        league: String,
        state: String,
        draft: Any?,
        checksum: String?,
        blockedReason: String?,
        policyGeneration: String? = null,
        evidenceHash: String = contentHash,
    ): Long {
        jdbc.update(
            """
            UPDATE league_import_item SET current_revision=?,current_source_version=?,current_content_hash=?,current_evidence_hash=?,
                classification=?,league_code=?,state=?,version=version+1,draft_json=?::jsonb,
                draft_checksum=?,blocked_reason=?,policy_generation=COALESCE(?,policy_generation),updated_at=now()
            WHERE id=?
            """.trimIndent(),
            revision, sourceVersion, contentHash, evidenceHash, classification, league, state,
            draft?.let { objectMapper.writeValueAsString(it) }, checksum, blockedReason, policyGeneration, itemId,
        )
        return jdbc.queryForObject("SELECT version FROM league_import_item WHERE id=?", Long::class.java, itemId)!!
    }

    fun updateRosterDraft(itemId: Long, status: String, draft: Any?, checksum: String?) {
        jdbc.update(
            "UPDATE league_import_item SET roster_status=?,roster_draft_json=?::jsonb,roster_checksum=?,updated_at=now() WHERE id=?",
            status, draft?.let { objectMapper.writeValueAsString(it) }, checksum, itemId,
        )
    }

    fun invalidateUnusedActions(itemId: Long) {
        jdbc.update(
            "UPDATE league_import_action SET status='STALE',updated_at=now() WHERE item_id=? AND status IN ('NOTIFY_PENDING','OFFERED','CREATE_PENDING')",
            itemId,
        )
        jdbc.update(
            "UPDATE league_import_operator_outbox SET status='SUPERSEDED' WHERE item_id=? AND status='PENDING' AND event_type IN ('CREATE_PREVIEW','CREATE_CONFIRM','FINALIZE_PREVIEW','FINALIZE_CONFIRM')",
            itemId,
        )
    }

    fun cancelOpenActionsForItem(itemId: Long, reason: String) {
        jdbc.update(
            """
            UPDATE league_import_action SET status='CANCELLED',last_error=?,processing_started_at=NULL,updated_at=now()
            WHERE item_id=? AND status IN ('NOTIFY_PENDING','OFFERED','CALLBACK_RECEIVED','CREATE_PENDING','CREATING',
                                           'FINALIZE_PENDING','FINALIZING')
            """.trimIndent(),
            reason.take(512), itemId,
        )
        jdbc.update(
            "UPDATE league_import_operator_outbox SET status='SUPERSEDED',lease_until=NULL WHERE item_id=? AND status='PENDING'",
            itemId,
        )
    }

    fun cancelJobsThroughItemVersion(itemId: Long, maxItemVersion: Long, reason: String = "source revision changed"): Int {
        val cancelled = jdbc.update(
            """
            UPDATE league_import_job SET status='CANCELLED',completed_at=now(),lease_until=NULL,
                lease_token=NULL,last_error=?,updated_at=now()
            WHERE item_id=? AND item_version<=? AND status IN ('PENDING','RUNNING','WAITING')
            """.trimIndent(),
            reason.take(512), itemId, maxItemVersion,
        )
        if (cancelled > 0) {
            jdbc.update(
                """
                UPDATE league_import_operator_outbox o SET status='SUPERSEDED',lease_until=NULL
                WHERE o.status='PENDING' AND o.id IN (
                    SELECT pending_notification_outbox_id FROM league_import_job
                    WHERE item_id=? AND item_version<=? AND status='CANCELLED'
                      AND pending_notification_outbox_id IS NOT NULL
                )
                """.trimIndent(),
                itemId, maxItemVersion,
            )
        }
        return cancelled
    }

    fun createAction(
        id: UUID,
        itemId: Long,
        itemVersion: Long,
        sourceRevision: Int,
        checksum: String,
        policyGeneration: String,
        actionType: String,
        tokenHash: String,
        boundActorId: Long?,
        operatorChatId: Long,
        expiresAt: Instant,
        rosterChecksum: String? = null,
    ) {
        jdbc.update(
            """
            INSERT INTO league_import_action(
                id,item_id,item_version,source_revision,draft_checksum,policy_generation,action_type,status,token_hash,
                bound_actor_telegram_id,operator_chat_id,expires_at,roster_checksum
            ) VALUES(?,?,?,?,?,?,?,'NOTIFY_PENDING',?,?,?,?,?)
            """.trimIndent(),
            id, itemId, itemVersion, sourceRevision, checksum, policyGeneration, actionType, tokenHash,
            boundActorId, operatorChatId, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), rosterChecksum,
        )
    }

    fun findOpenActions(): List<LeagueImportActionRow> = jdbc.query(
        """
        SELECT * FROM league_import_action
        WHERE status IN ('NOTIFY_PENDING','OFFERED','CALLBACK_RECEIVED','CREATE_PENDING','CREATING',
                         'FINALIZE_PENDING','FINALIZING')
        ORDER BY created_at
        """.trimIndent(),
    ) { rs, _ -> action(rs) }

    fun cancelAction(id: UUID, reason: String): Boolean = jdbc.update(
        """
        UPDATE league_import_action
        SET status='CANCELLED',last_error=?,updated_at=now()
        WHERE id=? AND status IN ('NOTIFY_PENDING','OFFERED','CALLBACK_RECEIVED','CREATE_PENDING','CREATING',
                                  'FINALIZE_PENDING','FINALIZING')
        """.trimIndent(),
        reason.take(512), id,
    ) == 1

    fun findAction(id: UUID, lock: Boolean = false): LeagueImportActionRow? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return jdbc.query("SELECT * FROM league_import_action WHERE id=?$suffix", { rs, _ -> action(rs) }, id).firstOrNull()
    }

    fun recordCallback(
        actionId: UUID,
        expectedStatus: String,
        nextStatus: String,
        updateId: Long,
        queryId: String,
        actorId: Long,
        actorUsername: String?,
    ): Boolean = try {
        jdbc.update(
            """
            UPDATE league_import_action SET status=?,callback_update_id=?,callback_query_id=?,
                actor_telegram_id=?,actor_username=?,updated_at=now()
            WHERE id=? AND status=?
            """.trimIndent(),
            nextStatus, updateId, queryId, actorId, actorUsername?.take(128), actionId, expectedStatus,
        ) == 1
    } catch (_: DuplicateKeyException) {
        false
    }

    fun updateActionStatus(actionId: UUID, status: String, error: String? = null) {
        jdbc.update(
            "UPDATE league_import_action SET status=?,last_error=?,updated_at=now() WHERE id=?",
            status, error?.take(512), actionId,
        )
    }

    fun markActionOffered(actionId: UUID, messageId: Long, expiresAt: Instant? = null) {
        jdbc.update(
            "UPDATE league_import_action SET status='OFFERED',operator_message_id=?,expires_at=COALESCE(?,expires_at),updated_at=now() WHERE id=? AND status='NOTIFY_PENDING'",
            messageId, expiresAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }, actionId,
        )
    }

    fun insertCallbackInbox(
        updateId: Long,
        queryId: String,
        actionId: UUID?,
        tokenHash: String,
        operatorChatId: Long?,
        operatorMessageId: Long?,
        actorTelegramId: Long?,
        actorUsername: String?,
        actorIsBot: Boolean,
        ordinaryMessage: Boolean,
    ): Boolean = jdbc.update(
        """
        INSERT INTO league_import_callback_inbox(
            callback_update_id,callback_query_id,action_id,token_hash,operator_chat_id,
            operator_message_id,actor_telegram_id,actor_username,actor_is_bot,ordinary_message
        ) VALUES(?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT DO NOTHING
        """.trimIndent(),
        updateId, queryId.take(128), actionId, tokenHash, operatorChatId, operatorMessageId,
        actorTelegramId, actorUsername?.take(128), actorIsBot, ordinaryMessage,
    ) == 1

    fun leaseCallbackInbox(): LeagueImportCallbackInboxRow? = jdbc.query(
        """
        UPDATE league_import_callback_inbox SET lease_until=now()+interval '60 seconds',attempts=attempts+1
        WHERE id=(
            SELECT id FROM league_import_callback_inbox
            WHERE status='PENDING' AND available_at<=now() AND (lease_until IS NULL OR lease_until<=now())
            ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
        ) RETURNING *
        """.trimIndent(),
        { rs, _ -> callbackInbox(rs) },
    ).firstOrNull()

    fun finishCallbackInbox(id: Long, processed: Boolean, answer: String? = null, error: String? = null) {
        if (processed) {
            jdbc.update(
                "UPDATE league_import_callback_inbox SET status='PROCESSED',processed_at=now(),answer_text=?,lease_until=NULL,last_error=NULL WHERE id=?",
                answer?.take(256), id,
            )
        } else {
            jdbc.update(
                "UPDATE league_import_callback_inbox SET available_at=now()+interval '5 seconds',lease_until=NULL,last_error=? WHERE id=?",
                error?.take(512), id,
            )
        }
    }

    fun enqueueOutbox(eventKey: String, itemId: Long, actionId: UUID?, eventType: String, payload: Any): Long {
        jdbc.update(
            """
            INSERT INTO league_import_operator_outbox(event_key,item_id,action_id,event_type,payload)
            VALUES(?,?,?,?,?::jsonb) ON CONFLICT(event_key) DO NOTHING
            """.trimIndent(),
            eventKey, itemId, actionId, eventType, objectMapper.writeValueAsString(payload),
        )
        return jdbc.queryForObject(
            "SELECT id FROM league_import_operator_outbox WHERE event_key=?",
            Long::class.java,
            eventKey,
        )!!
    }

    fun leaseOutbox(): LeagueImportOutboxRow? = jdbc.query(
        """
        UPDATE league_import_operator_outbox SET lease_until=now()+interval '60 seconds', attempts=attempts+1
        WHERE id=(
            SELECT id FROM league_import_operator_outbox
            WHERE status='PENDING' AND available_at<=now() AND (lease_until IS NULL OR lease_until<=now())
            ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
        ) RETURNING *
        """.trimIndent(),
        { rs, _ -> outbox(rs) },
    ).firstOrNull()

    fun finishOutbox(id: Long, delivered: Boolean, messageId: Long? = null, terminal: Boolean = false, error: String? = null) {
        if (delivered) {
            jdbc.update(
                "UPDATE league_import_operator_outbox SET status='DELIVERED',delivered_at=now(),telegram_message_id=?,lease_until=NULL,last_error=NULL WHERE id=?",
                messageId, id,
            )
        } else if (terminal) {
            jdbc.update(
                "UPDATE league_import_operator_outbox SET status='FAILED',lease_until=NULL,last_error=? WHERE id=?",
                error?.take(512), id,
            )
        } else {
            jdbc.update(
                "UPDATE league_import_operator_outbox SET available_at=now()+interval '30 seconds',lease_until=NULL,last_error=? WHERE id=?",
                error?.take(512), id,
            )
        }
    }

    fun releaseJobsAfterNotificationDelivery(outboxId: Long, holdSeconds: Long): Int = jdbc.update(
        """
        UPDATE league_import_job j
        SET notification_delivered_at=o.delivered_at,
            available_at=o.delivered_at + (? * interval '1 second'),updated_at=now()
        FROM league_import_operator_outbox o
        WHERE o.id=? AND o.status='DELIVERED' AND o.delivered_at IS NOT NULL
          AND j.pending_notification_outbox_id=o.id
          AND j.status IN ('PENDING','WAITING') AND j.notification_delivered_at IS NULL
        """.trimIndent(),
        holdSeconds, outboxId,
    )

    fun supersedeOutbox(id: Long) {
        jdbc.update("UPDATE league_import_operator_outbox SET status='SUPERSEDED',lease_until=NULL WHERE id=? AND status='PENDING'", id)
    }

    fun claimNextCreateAction(): UUID? = jdbc.query(
        """
        UPDATE league_import_action SET status='CREATING',attempts=attempts+1,
            processing_started_at=now(),updated_at=now()
        WHERE id=(
            SELECT id FROM league_import_action WHERE status='CREATE_PENDING'
            ORDER BY updated_at FOR UPDATE SKIP LOCKED LIMIT 1
        ) RETURNING id
        """.trimIndent(),
        { rs, _ -> rs.getObject(1, UUID::class.java) },
    ).firstOrNull()

    fun requeueStaleCreatingActions(): Int = jdbc.update(
        """
        UPDATE league_import_action SET status='CREATE_PENDING',processing_started_at=NULL,
            last_error='recovered stale create claim',updated_at=now()
        WHERE status='CREATING' AND processing_started_at < now()-interval '5 minutes'
        """.trimIndent(),
    )

    fun cancelAllOpenActions(reason: String): List<LeagueImportActionRow> = jdbc.query(
        """
        UPDATE league_import_action SET status='CANCELLED',last_error=?,processing_started_at=NULL,updated_at=now()
        WHERE status IN ('NOTIFY_PENDING','OFFERED','CALLBACK_RECEIVED','CREATE_PENDING','CREATING',
                         'FINALIZE_PENDING','FINALIZING')
        RETURNING *
        """.trimIndent(),
        { rs, _ -> action(rs) },
        reason.take(512),
    )

    fun updateItemApplied(itemId: Long, seriesId: Long) {
        jdbc.update("UPDATE league_import_item SET state='APPLIED',target_series_id=?,version=version+1,updated_at=now() WHERE id=?", seriesId, itemId)
    }

    fun updateItemState(itemId: Long, state: String, reason: String? = null) {
        jdbc.update("UPDATE league_import_item SET state=?,blocked_reason=?,updated_at=now() WHERE id=?", state, reason?.take(512), itemId)
    }

    fun updateItemTargetSeries(itemId: Long, seriesId: Long) {
        jdbc.update("UPDATE league_import_item SET target_series_id=?,updated_at=now() WHERE id=?", seriesId, itemId)
    }

    fun resetResultItemForReconcile(itemId: Long, seriesId: Long) {
        jdbc.update(
            """
            UPDATE league_import_item SET target_series_id=?,state='WAITING_FOR_GAMES',blocked_reason=NULL,
                readiness_status=NULL,readiness_checksum=NULL,stable_poll_count=0,ready_since=NULL,
                last_stable_observation_at=NULL,last_reconciled_at=NULL,updated_at=now()
            WHERE id=?
            """.trimIndent(),
            seriesId, itemId,
        )
    }

    fun updateItemReadiness(
        itemId: Long,
        state: String,
        readinessStatus: String,
        checksum: String?,
        stablePollCount: Int,
        readySince: Instant?,
        observedAt: Instant,
        reason: String? = null,
    ) {
        jdbc.update(
            """
            UPDATE league_import_item SET state=?,readiness_status=?,readiness_checksum=?,stable_poll_count=?,
                ready_since=?,last_stable_observation_at=?,last_reconciled_at=now(),blocked_reason=?,updated_at=now()
            WHERE id=?
            """.trimIndent(),
            state, readinessStatus, checksum, stablePollCount,
            readySince?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
            OffsetDateTime.ofInstant(observedAt, ZoneOffset.UTC), reason?.take(512), itemId,
        )
    }

    fun insertSourceLink(item: LeagueImportItemRow, seriesId: Long) {
        insertSourceLink(item, seriesId, "ANNOUNCEMENT")
    }

    fun insertSourceLink(item: LeagueImportItemRow, seriesId: Long, role: String) {
        val updated = jdbc.update(
            """
            UPDATE series_external_post_link SET import_item_id=?,source_revision=?,content_hash=?
            WHERE series_id=? AND source_channel_id=? AND source_message_id=? AND link_role=?
            """.trimIndent(),
            item.id, item.currentRevision, item.currentContentHash,
            seriesId, item.sourceChannelId, item.sourceMessageId, role,
        )
        if (updated == 1) return
        val inserted = jdbc.update(
            """
            INSERT INTO series_external_post_link(
                series_id,import_item_id,source_channel_id,source_message_id,link_role,source_revision,content_hash
            ) VALUES(?,?,?,?,?,?,?)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            seriesId, item.id, item.sourceChannelId, item.sourceMessageId, role, item.currentRevision, item.currentContentHash,
        )
        if (inserted != 1) {
            throw DuplicateKeyException("$role source link conflicts with an existing source or series role")
        }
    }

    fun linkedSeriesIdForSource(sourceChannelId: Long, sourceMessageId: Long, role: String): Long? =
        jdbc.query(
            "SELECT series_id FROM series_external_post_link WHERE source_channel_id=? AND source_message_id=? AND link_role=?",
            { rs, _ -> rs.getLong(1) }, sourceChannelId, sourceMessageId, role,
        ).firstOrNull()

    fun linkedMessageIdForSeriesRole(seriesId: Long, role: String): Long? =
        jdbc.query(
            "SELECT source_message_id FROM series_external_post_link WHERE series_id=? AND link_role=?",
            { rs, _ -> rs.getLong(1) }, seriesId, role,
        ).firstOrNull()

    fun hasSourceLinks(seriesId: Long): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM series_external_post_link WHERE series_id=?)",
        Boolean::class.java,
        seriesId,
    ) == true

    fun insertSourceLinkStrict(item: LeagueImportItemRow, seriesId: Long, role: String): Boolean =
        jdbc.update(
            """
            INSERT INTO series_external_post_link(
                series_id,import_item_id,source_channel_id,source_message_id,link_role,source_revision,content_hash
            ) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """.trimIndent(),
            seriesId, item.id, item.sourceChannelId, item.sourceMessageId, role,
            item.currentRevision, item.currentContentHash,
        ) == 1

    fun findAnnouncementLinkedSeriesIds(tournamentId: Long, publicNumber: Long): List<Long> = jdbc.query(
        """
        SELECT s.id
        FROM series s
        WHERE s.tournament_id=? AND s.public_number=?
          AND EXISTS (
            SELECT 1 FROM series_external_post_link l
            WHERE l.series_id=s.id AND l.link_role='ANNOUNCEMENT'
          )
        ORDER BY s.id
        """.trimIndent(),
        { rs, _ -> rs.getLong(1) },
        tournamentId, publicNumber,
    )

    fun findCurrentResultItemsForSeries(seriesId: Long, lock: Boolean = false): List<LeagueImportItemRow> {
        val lockClause = if (lock) " FOR UPDATE OF i" else ""
        return jdbc.query(
        """
        SELECT i.* FROM league_import_item i
        JOIN series_external_post_link l ON l.import_item_id=i.id AND l.link_role='RESULT'
        WHERE l.series_id=? AND i.classification='RESULT'
          AND l.source_revision=i.current_revision AND l.content_hash=i.current_content_hash
          AND EXISTS (SELECT 1 FROM league_import_revision r WHERE r.item_id=i.id AND r.is_current=TRUE
                      AND r.revision=i.current_revision AND r.content_hash=i.current_content_hash)
        ORDER BY i.id$lockClause
        """.trimIndent(),
        { rs, _ -> item(rs) }, seriesId,
    )
    }

    fun enqueueJob(
        itemId: Long,
        itemVersion: Long,
        sourceRevision: Int,
        evidenceChecksum: String,
        policyGeneration: String,
        operation: String,
        targetSeriesId: Long?,
        actorTelegramId: Long? = null,
        readinessChecksum: String? = null,
        availableAt: Instant? = Instant.now(),
        pendingNotificationOutboxId: Long? = null,
        rosterChecksum: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO league_import_job(
                id,item_id,item_version,source_revision,evidence_checksum,policy_generation,operation,
                target_series_id,actor_telegram_id,readiness_checksum,available_at,pending_notification_outbox_id,roster_checksum
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (item_id,item_version,source_revision,evidence_checksum,policy_generation,operation) DO NOTHING
            """.trimIndent(),
            id, itemId, itemVersion, sourceRevision, evidenceChecksum, policyGeneration, operation,
            targetSeriesId, actorTelegramId, readinessChecksum,
            availableAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }, pendingNotificationOutboxId, rosterChecksum,
        )
        return jdbc.queryForObject(
            """
            SELECT id FROM league_import_job
            WHERE item_id=? AND item_version=? AND source_revision=? AND evidence_checksum=?
              AND policy_generation=? AND operation=?
            """.trimIndent(),
            UUID::class.java,
            itemId, itemVersion, sourceRevision, evidenceChecksum, policyGeneration, operation,
        )!!
    }

    fun leaseJob(leaseToken: UUID = UUID.randomUUID()): LeagueImportJobRow? = jdbc.query(
        """
        UPDATE league_import_job SET status='RUNNING',lease_until=now()+interval '5 minutes',
            lease_token=?,attempts=attempts+1,updated_at=now()
        WHERE id=(
            SELECT id FROM league_import_job
            WHERE ((status IN ('PENDING','WAITING') AND available_at<=now())
                   OR (status='RUNNING' AND lease_until<=now()))
              AND (lease_until IS NULL OR lease_until<=now())
              AND (pending_notification_outbox_id IS NULL OR notification_delivered_at IS NOT NULL)
            ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1
        ) RETURNING *
        """.trimIndent(),
        { rs, _ -> job(rs) },
        leaseToken,
    ).firstOrNull()

    fun finishJob(
        id: UUID,
        status: String,
        error: String? = null,
        readinessChecksum: String? = null,
        expectedLeaseToken: UUID? = null,
    ): Boolean {
        val terminal = status in setOf("APPLIED", "BLOCKED", "FAILED", "CANCELLED")
        val leasePredicate = if (expectedLeaseToken == null) "" else " AND lease_token=?"
        val args = mutableListOf<Any?>(status, error?.take(512), readinessChecksum, terminal, id)
        if (expectedLeaseToken != null) args += expectedLeaseToken
        return jdbc.update(
            """
                UPDATE league_import_job SET status=?,last_error=?,readiness_checksum=COALESCE(?,readiness_checksum),
                lease_until=NULL,lease_token=NULL,completed_at=CASE WHEN ? THEN now() ELSE NULL END,updated_at=now()
            WHERE id=?$leasePredicate
            """.trimIndent(),
            *args.toTypedArray(),
        ) == 1
    }

    fun rescheduleJob(
        id: UUID,
        availableAt: Instant,
        reason: String? = null,
        checksum: String? = null,
        expectedLeaseToken: UUID? = null,
    ): Boolean {
        val leasePredicate = if (expectedLeaseToken == null) "" else " AND lease_token=?"
        val args = mutableListOf<Any?>(OffsetDateTime.ofInstant(availableAt, ZoneOffset.UTC), reason?.take(512), checksum, id)
        if (expectedLeaseToken != null) args += expectedLeaseToken
        return jdbc.update(
            """
            UPDATE league_import_job SET status='WAITING',available_at=?,lease_until=NULL,lease_token=NULL,last_error=?,
                readiness_checksum=COALESCE(?,readiness_checksum),updated_at=now() WHERE id=?$leasePredicate
            """.trimIndent(),
            *args.toTypedArray(),
        ) == 1
    }

    fun cancelAllOpenJobs(reason: String, operations: Set<String>? = null): List<LeagueImportJobRow> {
        val operationPredicate = if (operations.isNullOrEmpty()) "" else
            " AND operation IN (${operations.joinToString(",") { "?" }})"
        val args = mutableListOf<Any?>(reason.take(512))
        if (!operations.isNullOrEmpty()) args.addAll(operations.sorted())
        return jdbc.query(
            """
            UPDATE league_import_job SET status='CANCELLED',last_error=?,lease_until=NULL,lease_token=NULL,
                completed_at=now(),updated_at=now()
            WHERE status IN ('PENDING','WAITING','RUNNING')$operationPredicate
            RETURNING *
            """.trimIndent(),
            { rs, _ -> job(rs) },
            *args.toTypedArray(),
        )
    }

    fun findOpenJobs(): List<LeagueImportJobRow> = jdbc.query(
        """
        SELECT * FROM league_import_job
        WHERE status IN ('PENDING','WAITING','RUNNING')
        ORDER BY created_at,id
        """.trimIndent(),
    ) { rs, _ -> job(rs) }

    fun cancelOpenJob(id: UUID, reason: String): Boolean = jdbc.update(
        """
        UPDATE league_import_job SET status='CANCELLED',last_error=?,lease_until=NULL,lease_token=NULL,
            completed_at=now(),updated_at=now()
        WHERE id=? AND status IN ('PENDING','WAITING','RUNNING')
        """.trimIndent(),
        reason.take(512), id,
    ).also { cancelled ->
        if (cancelled == 1) supersedeJobPendingNotification(id)
    } == 1

    private fun supersedeJobPendingNotification(jobId: UUID) {
        jdbc.update(
            """
            UPDATE league_import_operator_outbox SET status='SUPERSEDED',lease_until=NULL
            WHERE status='PENDING' AND id=(
                SELECT pending_notification_outbox_id FROM league_import_job WHERE id=?
            )
            """.trimIndent(),
            jobId,
        )
    }

    fun findJob(id: UUID, lock: Boolean = false): LeagueImportJobRow? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return jdbc.query("SELECT * FROM league_import_job WHERE id=?$suffix", { rs, _ -> job(rs) }, id).firstOrNull()
    }

    fun audit(itemId: Long, actionId: UUID?, actorId: Long?, eventType: String, outcome: String, details: Any = emptyMap<String, Any>()) {
        jdbc.update(
            "INSERT INTO league_import_audit_event(item_id,action_id,actor_telegram_id,event_type,outcome,details) VALUES(?,?,?,?,?,?::jsonb)",
            itemId, actionId, actorId, eventType, outcome, objectMapper.writeValueAsString(details),
        )
    }

    fun seriesDuplicateExists(tournamentId: Long, publicNumber: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM series WHERE tournament_id=? AND public_number=?)",
            Boolean::class.java, tournamentId, publicNumber,
        ) == true

    private fun item(rs: ResultSet) = LeagueImportItemRow(
        id = rs.getLong("id"), sourceChannelId = rs.getLong("source_channel_id"),
        sourceMessageId = rs.getLong("source_message_id"), currentRevision = rs.getInt("current_revision"),
        currentSourceVersion = rs.getString("current_source_version"), currentContentHash = rs.getString("current_content_hash"),
        currentEvidenceHash = rs.getString("current_evidence_hash"),
        classification = rs.getString("classification"), leagueCode = rs.getString("league_code"),
        state = rs.getString("state"), version = rs.getLong("version"),
        draftJson = rs.getString("draft_json")?.let(objectMapper::readTree), draftChecksum = rs.getString("draft_checksum"),
        targetSeriesId = rs.getLong("target_series_id").takeUnless { rs.wasNull() },
        readinessStatus = rs.getString("readiness_status"), readinessChecksum = rs.getString("readiness_checksum"),
        stablePollCount = rs.getInt("stable_poll_count"),
        readySince = rs.getObject("ready_since", OffsetDateTime::class.java)?.toInstant(),
        lastStableObservationAt = rs.getObject("last_stable_observation_at", OffsetDateTime::class.java)?.toInstant(),
        policyGeneration = rs.getString("policy_generation"),
        rosterStatus = rs.getString("roster_status"),
        rosterDraftJson = rs.getString("roster_draft_json")?.let(objectMapper::readTree),
        rosterChecksum = rs.getString("roster_checksum"),
    )

    private fun revision(rs: ResultSet) = LeagueImportRevisionRow(
        revision = rs.getInt("revision"), postedAt = rs.getObject("posted_at", OffsetDateTime::class.java).toInstant(),
        editedAt = rs.getObject("edited_at", OffsetDateTime::class.java)?.toInstant(), contentHash = rs.getString("content_hash"),
        evidenceHash = rs.getString("evidence_hash"), rawText = rs.getString("raw_text"),
        mediaEvidence = rs.getString("media_evidence")?.let(objectMapper::readTree),
        observedAt = rs.getObject("observed_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun job(rs: ResultSet) = LeagueImportJobRow(
        id = rs.getObject("id", UUID::class.java), itemId = rs.getLong("item_id"), itemVersion = rs.getLong("item_version"),
        sourceRevision = rs.getInt("source_revision"), evidenceChecksum = rs.getString("evidence_checksum"),
        policyGeneration = rs.getString("policy_generation"), operation = rs.getString("operation"),
        status = rs.getString("status"), targetSeriesId = rs.getLong("target_series_id").takeUnless { rs.wasNull() },
        readinessChecksum = rs.getString("readiness_checksum"),
        actorTelegramId = rs.getLong("actor_telegram_id").takeUnless { rs.wasNull() }, attempts = rs.getInt("attempts"),
        leaseToken = rs.getObject("lease_token", UUID::class.java),
        availableAt = rs.getObject("available_at", OffsetDateTime::class.java)?.toInstant(),
        pendingNotificationOutboxId = rs.getLong("pending_notification_outbox_id").takeUnless { rs.wasNull() },
        notificationDeliveredAt = rs.getObject("notification_delivered_at", OffsetDateTime::class.java)?.toInstant(),
        rosterChecksum = rs.getString("roster_checksum"),
    )

    private fun action(rs: ResultSet) = LeagueImportActionRow(
        id = rs.getObject("id", UUID::class.java), itemId = rs.getLong("item_id"), itemVersion = rs.getLong("item_version"),
        sourceRevision = rs.getInt("source_revision"), draftChecksum = rs.getString("draft_checksum"),
        policyGeneration = rs.getString("policy_generation"),
        actionType = rs.getString("action_type"), status = rs.getString("status"), tokenHash = rs.getString("token_hash"),
        boundActorTelegramId = rs.getLong("bound_actor_telegram_id").takeUnless { rs.wasNull() },
        actorTelegramId = rs.getLong("actor_telegram_id").takeUnless { rs.wasNull() }, operatorChatId = rs.getLong("operator_chat_id"),
        operatorMessageId = rs.getLong("operator_message_id").takeUnless { rs.wasNull() },
        expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
        rosterChecksum = rs.getString("roster_checksum"),
    )

    private fun outbox(rs: ResultSet) = LeagueImportOutboxRow(
        id = rs.getLong("id"), eventKey = rs.getString("event_key"), itemId = rs.getLong("item_id"),
        actionId = rs.getObject("action_id", UUID::class.java), eventType = rs.getString("event_type"),
        payload = objectMapper.readTree(rs.getString("payload")),
    )

    private fun callbackInbox(rs: ResultSet) = LeagueImportCallbackInboxRow(
        id = rs.getLong("id"), updateId = rs.getLong("callback_update_id"), queryId = rs.getString("callback_query_id"),
        actionId = rs.getObject("action_id", UUID::class.java), tokenHash = rs.getString("token_hash"),
        operatorChatId = rs.getLong("operator_chat_id").takeUnless { rs.wasNull() },
        operatorMessageId = rs.getLong("operator_message_id").takeUnless { rs.wasNull() },
        actorTelegramId = rs.getLong("actor_telegram_id").takeUnless { rs.wasNull() },
        actorUsername = rs.getString("actor_username"), actorIsBot = rs.getBoolean("actor_is_bot"),
        ordinaryMessage = rs.getBoolean("ordinary_message"),
    )
}
