import sqlite3
import tempfile
import unittest
from pathlib import Path

from telegram_import.storage import Inbox


class StorageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.inbox = Inbox(Path(self.temp.name) / "shadow-inbox.sqlite")

    def tearDown(self):
        self.inbox.close()
        self.temp.cleanup()

    def persist(self, fingerprint, classification="ANNOUNCEMENT", silent=False, watermark=None, source_version="2026-08-07T00:00:00+00:00"):
        return self.inbox.persist(peer_id=-10042, message_id=7, source_version=source_version, message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=123, media_kind="MessageMediaPhoto", media_id="9001", text="#анонс_зл 08.08 20:00", fingerprint=fingerprint, classification=classification, league="ЗЛ", reason="test", silent=silent, watermark=watermark)

    def test_duplicate_is_noop_and_watermark_is_atomic(self):
        self.assertEqual(self.persist("a", watermark=7), "CANDIDATE")
        self.assertEqual(self.persist("a", watermark=8), "DUPLICATE")
        self.assertEqual(self.inbox.get_state("watermark"), "8")
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM revisions").fetchone()[0], 1)

    def test_pending_revision_is_superseded(self):
        self.persist("a")
        self.assertEqual(self.persist("b", source_version="2026-08-07T00:01:00+00:00"), "CANDIDATE")
        rows = self.inbox.connection.execute("SELECT event_type,status FROM outbox ORDER BY id").fetchall()
        self.assertEqual([tuple(row) for row in rows], [("CANDIDATE", "SUPERSEDED"), ("CANDIDATE", "PENDING")])

    def test_undelivered_candidate_that_becomes_ignore_is_suppressed(self):
        self.persist("a")
        self.assertEqual(self.persist("b", classification="IGNORE", source_version="2026-08-07T00:01:00+00:00"), "STORED")
        self.assertEqual(self.inbox.connection.execute("SELECT status FROM outbox").fetchone()[0], "SUPERSEDED")

    def test_delivered_candidate_retraction_is_retained(self):
        self.persist("a")
        row = self.inbox.lease_outbox()
        self.inbox.finish_outbox(row["id"], delivered=True)
        self.assertEqual(self.persist("b", classification="IGNORE", source_version="2026-08-07T00:01:00+00:00"), "RETRACTED")
        self.assertEqual(self.inbox.connection.execute("SELECT status FROM outbox ORDER BY id").fetchall()[0][0], "DELIVERED")

    def test_silent_backfill_has_no_outbox(self):
        self.assertEqual(self.persist("a", silent=True), "STORED")
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM outbox").fetchone()[0], 0)

    def test_silent_backfill_has_no_backend_outbox(self):
        self.inbox.persist(peer_id=-10042, message_id=7, source_version="2026-08-07T00:00:00+00:00", message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None, media_kind=None, media_id=None, text="#анонс_зл Серия 7 08.08 20:00", fingerprint="backend-silent", classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=True, backend_delivery=True)
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 0)

    def test_targeted_backend_handoff_enqueues_known_revision_once(self):
        self.persist("known-direct")
        common = dict(
            peer_id=-10042,
            message_id=7,
            source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00",
            edit_date=None,
            grouped_id=123,
            media_kind="MessageMediaPhoto",
            media_id="9001",
            text="#анонс_зл 08.08 20:00",
            fingerprint="known-direct",
            classification="ANNOUNCEMENT",
            league="ЗЛ",
            reason="targeted handoff",
            silent=False,
            backend_delivery=True,
            force_backend_delivery=True,
        )

        self.assertEqual(self.inbox.persist(**common), "DUPLICATE")
        self.assertEqual(self.inbox.persist(**common), "DUPLICATE")
        rows = self.inbox.connection.execute("SELECT event_key,status FROM backend_outbox").fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["event_key"], "tg:-10042:7:r1")
        self.assertEqual(rows[0]["status"], "PENDING")

    def test_backend_delivery_is_atomic_and_suppresses_direct_outbox(self):
        self.inbox.persist(peer_id=-10042, message_id=7, source_version="2026-08-07T00:00:00+00:00", message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None, media_kind=None, media_id=None, text="#анонс_зл Серия 7 08.08 20:00", fingerprint="backend-a", classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=False, backend_delivery=True)
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM outbox").fetchone()[0], 0)
        row = self.inbox.connection.execute("SELECT delivery_id,payload,status FROM backend_outbox").fetchone()
        self.assertEqual(row[2], "PENDING")
        self.assertIn('"sourceChannelPeerId":-10042', row[1])

    def test_schema_migrates_to_v3(self):
        self.assertEqual(self.inbox.connection.execute("PRAGMA user_version").fetchone()[0], 3)
        self.assertIsNotNone(self.inbox.connection.execute("SELECT name FROM sqlite_master WHERE name='backend_outbox'").fetchone())
        self.assertIsNotNone(self.inbox.connection.execute("SELECT name FROM sqlite_master WHERE name='ocr_tasks'").fetchone())

    def test_v2_schema_migrates_to_v3_without_recreating_audit_tables(self):
        self.inbox.close()
        path = Path(self.temp.name) / "v2.sqlite"
        db = sqlite3.connect(path)
        db.execute("CREATE TABLE revisions(id INTEGER PRIMARY KEY)")
        db.execute("PRAGMA user_version=2")
        db.close()

        migrated = Inbox(path)
        try:
            self.assertEqual(migrated.connection.execute("PRAGMA user_version").fetchone()[0], 3)
            self.assertIsNotNone(migrated.connection.execute("SELECT name FROM sqlite_master WHERE name='ocr_tasks'").fetchone())
        finally:
            migrated.close()

    def test_photo_ocr_delays_backend_evidence_until_durable_completion(self):
        self.inbox.persist(
            peer_id=-10042, message_id=8, source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None,
            media_kind="MessageMediaPhoto", media_id="9010", text="#анонс_зл 08.08 20:00",
            fingerprint="photo-ocr", classification="ANNOUNCEMENT", league="ЗЛ", reason="test",
            silent=False, backend_delivery=True, ocr_enabled=True,
        )
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 0)
        task = self.inbox.lease_ocr_task()
        ocr = {
            "status":"SUCCESS", "provider":"YANDEX_VISION", "model":"page",
            "languageCodes":["ru","en"], "checksum":"a" * 64,
            "fullText":"Player", "lines":[{"text":"Player","boundingBox":[{"x":1,"y":2}]}],
            "errorCode":None,
        }
        media = {
            "telegramMediaId":"9010", "groupedId":None, "mimeType":"image/jpeg",
            "byteSize":123, "width":100, "height":200, "sha256":"b" * 64, "ocr":ocr,
        }
        self.assertTrue(self.inbox.complete_ocr_task(task["id"], task["lease_token"], media))
        row = self.inbox.connection.execute("SELECT payload,status FROM backend_outbox").fetchone()
        self.assertEqual(row["status"], "PENDING")
        self.assertIn('"mediaEvidence"', row["payload"])
        self.assertIn('"evidenceHash"', row["payload"])

    def test_ocr_evidence_gets_new_event_after_legacy_text_event_was_delivered(self):
        common = dict(
            peer_id=-10042, message_id=81, source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None,
            media_kind="MessageMediaPhoto", media_id="9011", text="#анонс_зл 08.08 20:00",
            fingerprint="photo-upgrade", classification="ANNOUNCEMENT", league="ЗЛ", reason="test",
            silent=False, backend_delivery=True,
        )
        self.inbox.persist(**common)
        legacy = self.inbox.lease_backend_outbox()
        self.inbox.finish_backend_outbox(legacy["id"], delivered=True)
        self.assertEqual(
            self.inbox.persist(**common, force_backend_delivery=True, ocr_enabled=True),
            "DUPLICATE",
        )
        task = self.inbox.lease_ocr_task()
        media = {
            "telegramMediaId":"9011", "groupedId":None, "mimeType":"image/jpeg",
            "byteSize":123, "width":100, "height":200, "sha256":"b" * 64,
            "ocr":{
                "status":"SUCCESS", "provider":"YANDEX_VISION", "model":"page",
                "languageCodes":["ru","en"], "checksum":"a" * 64,
                "fullText":"Player", "lines":[], "errorCode":None,
            },
        }
        self.assertTrue(self.inbox.complete_ocr_task(task["id"], task["lease_token"], media))

        rows = self.inbox.connection.execute(
            "SELECT event_key,delivery_id,status FROM backend_outbox ORDER BY id"
        ).fetchall()
        self.assertEqual([row["status"] for row in rows], ["DELIVERED", "PENDING"])
        self.assertEqual(rows[0]["event_key"], "tg:-10042:81:r1")
        self.assertTrue(rows[1]["event_key"].startswith("tg:-10042:81:r1:e:"))
        ocr_delivery_id = rows[1]["delivery_id"]
        self.inbox.connection.execute(
            "UPDATE ocr_tasks SET status='RUNNING',lease_token='exact-replay',lease_until='2099-01-01T00:00:00+00:00' WHERE id=?",
            (task["id"],),
        )
        self.assertTrue(self.inbox.complete_ocr_task(task["id"], "exact-replay", media))
        self.assertEqual(
            self.inbox.connection.execute("SELECT delivery_id FROM backend_outbox WHERE event_key=?", (rows[1]["event_key"],)).fetchone()[0],
            ocr_delivery_id,
        )
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 2)

    def test_only_explicit_reclassify_rearms_terminal_failed_ocr(self):
        common = dict(
            peer_id=-10042, message_id=82, source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None,
            media_kind="MessageMediaPhoto", media_id="9012", text="#анонс_зл 08.08 20:00",
            fingerprint="photo-failed", classification="ANNOUNCEMENT", league="ЗЛ", reason="test",
            silent=False, backend_delivery=True, ocr_enabled=True,
        )
        self.inbox.persist(**common)
        task = self.inbox.lease_ocr_task()
        failed = {
            "telegramMediaId":"9012", "groupedId":None, "mimeType":None,
            "byteSize":None, "width":None, "height":None, "sha256":None,
            "ocr":{
                "status":"FAILED", "provider":"YANDEX_VISION", "model":"page",
                "languageCodes":["ru","en"], "checksum":"a" * 64,
                "fullText":"", "lines":[], "errorCode":"PROVIDER_RETRIES_EXHAUSTED",
            },
        }
        self.assertTrue(self.inbox.complete_ocr_task(task["id"], task["lease_token"], failed))
        self.assertEqual(self.inbox.persist(**common), "DUPLICATE")
        self.assertEqual(self.inbox.connection.execute("SELECT status FROM ocr_tasks").fetchone()[0], "FAILED")

        self.assertEqual(
            self.inbox.persist(**common, force_backend_delivery=True),
            "DUPLICATE",
        )
        reset = self.inbox.connection.execute(
            "SELECT status,attempts,evidence_json,completed_at,last_error FROM ocr_tasks"
        ).fetchone()
        self.assertEqual(tuple(reset), ("PENDING", 0, None, None, None))

    def test_newer_semantic_duplicate_requeues_source_changed_ocr(self):
        common = dict(
            peer_id=-10042, message_id=84,
            message_date="2026-08-07T00:00:00+00:00", edit_date=None,
            grouped_id=None, media_kind="MessageMediaPhoto", media_id="9014",
            text="#анонс_зл 08.08 20:00", fingerprint="photo-source-version",
            classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=False,
            backend_delivery=True, ocr_enabled=True,
        )
        self.inbox.persist(source_version="2026-08-07T00:00:00+00:00", **common)
        task = self.inbox.lease_ocr_task()
        self.assertTrue(self.inbox.supersede_ocr_task(task["id"], task["lease_token"], "source media changed"))

        self.assertEqual(
            self.inbox.persist(source_version="2026-08-07T00:01:00+00:00", **common),
            "DUPLICATE",
        )

        requeued = self.inbox.connection.execute(
            "SELECT source_version,status,attempts,completed_at,last_error FROM ocr_tasks"
        ).fetchone()
        self.assertEqual(
            tuple(requeued),
            ("2026-08-07T00:01:00+00:00", "PENDING", 0, None, None),
        )

    def test_same_version_duplicate_does_not_loop_source_changed_ocr(self):
        common = dict(
            peer_id=-10042, message_id=85,
            source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None,
            grouped_id=None, media_kind="MessageMediaPhoto", media_id="9015",
            text="#анонс_зл 08.08 20:00", fingerprint="photo-same-version",
            classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=False,
            backend_delivery=True, ocr_enabled=True,
        )
        self.inbox.persist(**common)
        task = self.inbox.lease_ocr_task()
        self.assertTrue(self.inbox.supersede_ocr_task(task["id"], task["lease_token"], "source media changed"))

        self.assertEqual(self.inbox.persist(**common), "DUPLICATE")
        terminal = self.inbox.connection.execute("SELECT status,last_error FROM ocr_tasks").fetchone()
        self.assertEqual(tuple(terminal), ("SUPERSEDED", "source media changed"))

    def test_explicit_reclassify_requeues_saved_success_after_terminal_backend_failure(self):
        common = dict(
            peer_id=-10042, message_id=83, source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None,
            media_kind="MessageMediaPhoto", media_id="9013", text="#анонс_зл 08.08 20:00",
            fingerprint="photo-success", classification="ANNOUNCEMENT", league="ЗЛ", reason="test",
            silent=False, backend_delivery=True, ocr_enabled=True,
        )
        self.inbox.persist(**common)
        task = self.inbox.lease_ocr_task()
        media = {
            "telegramMediaId":"9013", "groupedId":None, "mimeType":"image/jpeg",
            "byteSize":123, "width":100, "height":200, "sha256":"b" * 64,
            "ocr":{
                "status":"SUCCESS", "provider":"YANDEX_VISION", "model":"page",
                "languageCodes":["ru","en"], "checksum":"a" * 64,
                "fullText":"Player", "lines":[], "errorCode":None,
            },
        }
        self.assertTrue(self.inbox.complete_ocr_task(task["id"], task["lease_token"], media))
        outbox = self.inbox.lease_backend_outbox()
        delivery_id = outbox["delivery_id"]
        self.inbox.finish_backend_outbox(outbox["id"], delivered=False, terminal=True, error="HTTP 400")

        self.assertEqual(
            self.inbox.persist(**common, force_backend_delivery=True),
            "DUPLICATE",
        )
        replay = self.inbox.connection.execute("SELECT status,delivery_id FROM backend_outbox").fetchone()
        self.assertEqual((replay["status"], replay["delivery_id"]), ("PENDING", delivery_id))
        self.assertEqual(self.inbox.connection.execute("SELECT status FROM ocr_tasks").fetchone()[0], "SUCCESS")

    def test_edit_fences_running_ocr_and_supersedes_old_pending_evidence(self):
        common = dict(
            peer_id=-10042, message_id=9, message_date="2026-08-07T00:00:00+00:00",
            edit_date=None, grouped_id=None, media_kind="MessageMediaPhoto", media_id="9020",
            classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=False,
            backend_delivery=True, ocr_enabled=True,
        )
        self.inbox.persist(source_version="2026-08-07T00:00:00+00:00", text="#анонс_зл 08.08 20:00", fingerprint="r1", **common)
        old = self.inbox.lease_ocr_task()
        self.inbox.persist(source_version="2026-08-07T00:01:00+00:00", text="#анонс_зл 08.08 21:00", fingerprint="r2", **common)
        fenced = self.inbox.connection.execute("SELECT status,lease_token FROM ocr_tasks WHERE id=?", (old["id"],)).fetchone()
        self.assertEqual(tuple(fenced), ("SUPERSEDED", None))
        self.assertFalse(self.inbox.complete_ocr_task(old["id"], old["lease_token"], {}))

    def test_expired_running_ocr_is_released_with_a_new_fencing_token(self):
        self.inbox.persist(
            peer_id=-10042, message_id=10, source_version="2026-08-07T00:00:00+00:00",
            message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None,
            media_kind="MessageMediaPhoto", media_id="9030", text="#анонс_зл 08.08 20:00",
            fingerprint="expired", classification="ANNOUNCEMENT", league="ЗЛ", reason="test",
            silent=False, backend_delivery=True, ocr_enabled=True,
        )
        first = self.inbox.lease_ocr_task()
        self.inbox.connection.execute(
            "UPDATE ocr_tasks SET lease_until='2000-01-01T00:00:00+00:00' WHERE id=?",
            (first["id"],),
        )
        second = self.inbox.lease_ocr_task()
        self.assertEqual(second["id"], first["id"])
        self.assertNotEqual(second["lease_token"], first["lease_token"])
        self.assertFalse(self.inbox.retry_ocr_task(first["id"], first["lease_token"], "2026-08-07T00:00:00+00:00", "stale"))

    def test_older_observation_is_audit_only(self):
        self.persist("new", source_version="2026-08-07T00:02:00+00:00")
        self.assertEqual(self.persist("old", classification="IGNORE", source_version="2026-08-07T00:01:00+00:00"), "STALE")
        row = self.inbox.connection.execute("SELECT classification,latest_revision FROM messages").fetchone()
        self.assertEqual(tuple(row), ("ANNOUNCEMENT", 1))
        self.assertEqual(self.inbox.connection.execute("SELECT is_latest FROM revisions ORDER BY revision_no").fetchall()[1][0], 0)

    def test_equal_timestamp_last_observed_content_wins(self):
        self.persist("first", source_version="2026-08-07T00:02:00+00:00")
        first = self.inbox.lease_outbox()
        self.inbox.finish_outbox(first["id"], delivered=True)
        self.assertEqual(self.persist("second", classification="IGNORE", source_version="2026-08-07T00:02:00+00:00"), "RETRACTED")
        self.assertEqual(self.inbox.connection.execute("SELECT classification FROM messages").fetchone()[0], "IGNORE")

    def test_foreign_keys_are_enabled_and_clean(self):
        self.assertEqual(self.inbox.connection.execute("PRAGMA foreign_keys").fetchone()[0], 1)
        self.assertEqual(self.inbox.connection.execute("PRAGMA foreign_key_check").fetchall(), [])

    def test_inspect_reports_pinned_identity_without_numeric_ids(self):
        with self.inbox.transaction() as db:
            self.inbox.set_state(db, "pinned_channel_peer_id", -10042)
            self.inbox.set_state(db, "pinned_account_user_id", 12345)

        summary = self.inbox.inspect_summary()

        self.assertTrue(summary["identityPinned"])
        self.assertNotIn("pinnedChannelPeerId", summary)
        self.assertNotIn("pinnedAccountUserId", summary)


if __name__ == "__main__":
    unittest.main()
