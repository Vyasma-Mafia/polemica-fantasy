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

    def test_backend_delivery_is_atomic_and_suppresses_direct_outbox(self):
        self.inbox.persist(peer_id=-10042, message_id=7, source_version="2026-08-07T00:00:00+00:00", message_date="2026-08-07T00:00:00+00:00", edit_date=None, grouped_id=None, media_kind=None, media_id=None, text="#анонс_зл Серия 7 08.08 20:00", fingerprint="backend-a", classification="ANNOUNCEMENT", league="ЗЛ", reason="test", silent=False, backend_delivery=True)
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM outbox").fetchone()[0], 0)
        row = self.inbox.connection.execute("SELECT delivery_id,payload,status FROM backend_outbox").fetchone()
        self.assertEqual(row[2], "PENDING")
        self.assertIn('"sourceChannelPeerId":-10042', row[1])

    def test_schema_migrates_v1_to_v2(self):
        self.assertEqual(self.inbox.connection.execute("PRAGMA user_version").fetchone()[0], 2)
        self.assertIsNotNone(self.inbox.connection.execute("SELECT name FROM sqlite_master WHERE name='backend_outbox'").fetchone())

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
