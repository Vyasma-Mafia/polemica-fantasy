import json
import hashlib
import os
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from telegram_import.config import DeliveryMode, OcrConfig
from telegram_import.ocr import MediaMetadata, OcrResult, ocr_checksum, terminal_ocr
from telegram_import.storage import Inbox
from telegram_import.telegram import drain_ocr_tasks, persist_message, prepare_ocr_spool


MessageMediaPhoto = type("MessageMediaPhoto", (), {})


def announcement(message_id=34, *, grouped_id=None, edit_date=None):
    media = MessageMediaPhoto()
    media.photo = SimpleNamespace(id=9001)
    return SimpleNamespace(
        id=message_id,
        message="#анонс_лп Дата: 11 августа Время: 19:00",
        date=datetime(2026, 8, 10, 12, tzinfo=timezone.utc),
        edit_date=edit_date,
        grouped_id=grouped_id,
        media=media,
    )


class FakeClient:
    def __init__(self, message):
        self.message = message
        self.downloads = 0
        self.download_mode = None

    async def get_messages(self, _entity, ids=None):
        return self.message if ids == self.message.id else None

    async def download_media(self, _message, file):
        self.downloads += 1
        self.download_mode = os.stat(file).st_mode & 0o777
        Path(file).write_bytes(b"fake-jpeg")
        return file


class FakeVision:
    def __init__(self, result):
        self.result = result
        self.calls = 0

    async def recognize(self, _path, _metadata):
        self.calls += 1
        return self.result


class OcrPipelineTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.spool = self.root / "spool"
        prepare_ocr_spool(self.spool)
        self.inbox = Inbox(self.root / "inbox.sqlite")
        self.config = OcrConfig(
            True,
            "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText",
            "secret",
            "folder",
        )

    async def asyncTearDown(self):
        self.inbox.close()
        self.temp.cleanup()

    def queue(self, message):
        result = persist_message(
            self.inbox,
            -10042,
            message,
            silent=False,
            mode=DeliveryMode.BACKEND,
            ocr_enabled=True,
        )
        self.assertEqual(result, "CANDIDATE")
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 0)

    async def test_single_photo_is_delivered_only_after_durable_ocr_evidence(self):
        message = announcement()
        self.queue(message)
        lines = ({"text": "Player", "boundingBox": [{"x": 1, "y": 2}]},)
        checksum = ocr_checksum("YANDEX_VISION", "page", ("ru", "en"), "Player", lines)
        vision = FakeVision(OcrResult("SUCCESS", "YANDEX_VISION", "page", ("ru", "en"), "Player", lines, checksum))
        metadata = MediaMetadata("image/jpeg", 9, 100, 200, "b" * 64)
        client = FakeClient(message)

        with patch("telegram_import.telegram.inspect_media", return_value=metadata):
            await drain_ocr_tasks(client, object(), self.inbox, vision, self.config, self.spool)

        task = self.inbox.connection.execute("SELECT status,evidence_json FROM ocr_tasks").fetchone()
        self.assertEqual(task["status"], "SUCCESS")
        self.assertEqual(json.loads(task["evidence_json"])["ocr"]["checksum"], checksum)
        payload = json.loads(self.inbox.connection.execute("SELECT payload FROM backend_outbox").fetchone()[0])
        self.assertEqual(payload["contentHash"], hashlib.sha256(message.message.encode()).hexdigest())
        self.assertEqual(payload["mediaEvidence"]["telegramMediaId"], "9001")
        self.assertEqual(payload["mediaEvidence"]["ocr"]["fullText"], "Player")
        self.assertEqual(len(payload["evidenceHash"]), 64)
        self.assertEqual(client.download_mode, 0o600)
        self.assertEqual(list(self.spool.iterdir()), [])

    async def test_album_is_terminal_unsupported_and_is_never_downloaded(self):
        message = announcement(grouped_id=777)
        self.queue(message)
        client = FakeClient(message)

        await drain_ocr_tasks(client, object(), self.inbox, FakeVision(None), self.config, self.spool)

        self.assertEqual(client.downloads, 0)
        task = self.inbox.connection.execute("SELECT status FROM ocr_tasks").fetchone()
        self.assertEqual(task[0], "UNSUPPORTED")
        payload = json.loads(self.inbox.connection.execute("SELECT payload FROM backend_outbox").fetchone()[0])
        self.assertEqual(payload["mediaEvidence"]["groupedId"], 777)
        self.assertEqual(payload["mediaEvidence"]["ocr"]["errorCode"], "ALBUM_UNSUPPORTED")

    async def test_retryable_provider_failure_is_durable_without_evidence_delivery(self):
        message = announcement()
        self.queue(message)
        retry = terminal_ocr("FAILED", self.config, "PROVIDER_UNAVAILABLE")
        retry = OcrResult(**{**retry.__dict__, "retryable": True, "retry_after": 60})
        client = FakeClient(message)

        with patch("telegram_import.telegram.inspect_media", return_value=MediaMetadata("image/jpeg", 9, 1, 1, "b" * 64)):
            await drain_ocr_tasks(client, object(), self.inbox, FakeVision(retry), self.config, self.spool)

        task = self.inbox.connection.execute("SELECT status,lease_token,last_error FROM ocr_tasks").fetchone()
        self.assertEqual((task["status"], task["lease_token"], task["last_error"]), ("RETRY", None, "PROVIDER_UNAVAILABLE"))
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 0)
        self.assertEqual(list(self.spool.iterdir()), [])

    async def test_exhausted_provider_failure_is_terminal_evidence(self):
        message = announcement()
        self.queue(message)
        config = OcrConfig(True, self.config.api_url, "secret", "folder", max_attempts=1)
        retry = terminal_ocr("FAILED", config, "PROVIDER_UNAVAILABLE")
        retry = OcrResult(**{**retry.__dict__, "retryable": True, "retry_after": 60})

        with patch("telegram_import.telegram.inspect_media", return_value=MediaMetadata("image/jpeg", 9, 1, 1, "b" * 64)):
            await drain_ocr_tasks(FakeClient(message), object(), self.inbox, FakeVision(retry), config, self.spool)

        payload = json.loads(self.inbox.connection.execute("SELECT payload FROM backend_outbox").fetchone()[0])
        self.assertEqual(payload["mediaEvidence"]["ocr"]["status"], "FAILED")
        self.assertEqual(payload["mediaEvidence"]["ocr"]["errorCode"], "PROVIDER_RETRIES_EXHAUSTED")

    async def test_lost_lease_keeps_raw_file_for_startup_cleanup(self):
        message = announcement()
        self.queue(message)
        client = FakeClient(message)
        succeeded = terminal_ocr("FAILED", self.config, "OCR_EMPTY")

        class LeaseStealingVision:
            async def recognize(inner_self, _path, _metadata):
                self.inbox.connection.execute("UPDATE ocr_tasks SET lease_token='new-owner' WHERE status='RUNNING'")
                return succeeded

        with patch("telegram_import.telegram.inspect_media", return_value=MediaMetadata("image/jpeg", 9, 1, 1, "b" * 64)):
            await drain_ocr_tasks(client, object(), self.inbox, LeaseStealingVision(), self.config, self.spool)

        self.assertEqual(len(list(self.spool.iterdir())), 1)
        prepare_ocr_spool(self.spool)
        self.assertEqual(list(self.spool.iterdir()), [])

    async def test_source_edit_during_provider_call_supersedes_ocr_before_delivery(self):
        message = announcement()
        self.queue(message)
        client = FakeClient(message)
        succeeded = terminal_ocr("FAILED", self.config, "OCR_EMPTY")

        class EditingVision:
            async def recognize(inner_self, _path, _metadata):
                client.message = announcement(edit_date=datetime(2026, 8, 10, 13, tzinfo=timezone.utc))
                return succeeded

        with patch("telegram_import.telegram.inspect_media", return_value=MediaMetadata("image/jpeg", 9, 1, 1, "b" * 64)):
            await drain_ocr_tasks(client, object(), self.inbox, EditingVision(), self.config, self.spool)

        task = self.inbox.connection.execute("SELECT status,last_error FROM ocr_tasks").fetchone()
        self.assertEqual(task["status"], "SUPERSEDED")
        self.assertIn("changed during OCR", task["last_error"])
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM backend_outbox").fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
