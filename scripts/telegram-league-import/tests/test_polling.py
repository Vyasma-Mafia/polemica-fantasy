import os
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from telegram_import.config import NotifierConfig
from telegram_import.notifier import BotNotifier
from telegram_import.storage import Inbox
from telegram_import.telegram import persist_message, poll_once


class FakeClient:
    def __init__(self, messages):
        self.messages = list(messages)

    async def get_messages(self, _entity, limit=None, ids=None):
        if ids is not None:
            by_id = {message.id: message for message in self.messages}
            return [by_id.get(message_id) for message_id in ids]
        return list(reversed(self.messages))[:limit]

    def iter_messages(self, _entity, *, limit=None, min_id=0, max_id=None, reverse=False):
        values = [m for m in self.messages if m.id > min_id and (max_id is None or m.id < max_id)]
        values.sort(key=lambda m: m.id, reverse=not reverse)
        if limit is not None:
            values = values[:limit]

        async def iterator():
            for value in values:
                yield value
        return iterator()


class ArrivingClient(FakeClient):
    def __init__(self, messages, arriving):
        super().__init__(messages)
        self.arriving = arriving
        self.latest_calls = 0

    async def get_messages(self, entity, limit=None, ids=None):
        if ids is None:
            self.latest_calls += 1
            if self.latest_calls == 2:
                self.messages.append(self.arriving)
        return await super().get_messages(entity, limit=limit, ids=ids)


def message(message_id, text, edit_date=None):
    return SimpleNamespace(id=message_id, message=text, date=datetime(2026, 8, 7, tzinfo=timezone.utc), edit_date=edit_date, grouped_id=None, media=None)


class PollingTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.inbox = Inbox(Path(self.temp.name) / "inbox.sqlite")
        self.notifier = BotNotifier(NotifierConfig(False, None, None, None))

    async def asyncTearDown(self):
        self.inbox.close()
        self.temp.cleanup()

    async def test_initial_high_watermark_is_silent_then_catchup_is_ascending(self):
        environment = {"TELEGRAM_IMPORT_INITIAL_BACKFILL": "10", "TELEGRAM_IMPORT_EDIT_SCAN": "2", "TELEGRAM_IMPORT_DEEP_SCAN": "2"}
        with patch.dict(os.environ, environment):
            await poll_once(FakeClient([message(1, "#анонс_зл 08.08 20:00"), message(2, "#анонс_лп 09.08 21:00")]), object(), -10042, self.inbox, self.notifier)
            self.assertEqual(self.inbox.get_state("watermark"), "2")
            self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM outbox").fetchone()[0], 0)
            await poll_once(FakeClient([message(1, "#анонс_зл 08.08 20:00"), message(2, "#анонс_лп 09.08 21:00"), message(3, "#анонс_зл 10.08 22:00"), message(4, "#результаты_лп игра №1 победа мафии")]), object(), -10042, self.inbox, self.notifier)
        ids = [row[0] for row in self.inbox.connection.execute("SELECT message_id FROM outbox ORDER BY id")]
        self.assertEqual(ids, [3, 4])
        self.assertEqual(self.inbox.get_state("watermark"), "4")

    async def test_arrival_during_initial_backfill_is_not_silenced(self):
        client = ArrivingClient([message(1, "#анонс_зл 08.08 20:00"), message(2, "#анонс_лп 09.08 21:00")], message(3, "#анонс_зл 10.08 22:00"))
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_INITIAL_BACKFILL":"10", "TELEGRAM_IMPORT_EDIT_SCAN":"2", "TELEGRAM_IMPORT_DEEP_SCAN":"2"}):
            await poll_once(client, object(), -10042, self.inbox, self.notifier)
        self.assertEqual(self.inbox.get_state("initial_high_watermark"), "2")
        self.assertEqual(self.inbox.get_state("watermark"), "3")
        self.assertEqual(self.inbox.connection.execute("SELECT message_id FROM outbox").fetchone()[0], 3)

    async def test_identical_content_with_new_edit_timestamp_is_noop(self):
        original = message(1, "#анонс_зл 08.08 20:00")
        edited = message(1, "#анонс_зл 08.08 20:00", datetime(2026,8,7,1,tzinfo=timezone.utc))
        self.assertEqual(persist_message(self.inbox,-10042,original,silent=False), "CANDIDATE")
        self.assertEqual(persist_message(self.inbox,-10042,edited,silent=False), "DUPLICATE")
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM revisions").fetchone()[0], 1)

    async def test_targeted_classifier_replay_creates_one_new_revision(self):
        announcement = message(2234, "#анонс_лп Дата: 11 августа Время: 19:00")
        original = message(2234, "#анонс_лп Дата: 11 августа Время: 19:00")
        with patch("telegram_import.telegram.classify") as classifier:
            classifier.return_value = SimpleNamespace(kind="IGNORE", league="ЛП", reason="old rules")
            self.assertEqual(persist_message(self.inbox, -10042, original, silent=False), "STORED")
        self.assertEqual(
            persist_message(self.inbox, -10042, announcement, silent=False, classifier_replay=True),
            "CANDIDATE",
        )
        self.assertEqual(
            persist_message(self.inbox, -10042, announcement, silent=False, classifier_replay=True),
            "DUPLICATE",
        )
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM revisions").fetchone()[0], 2)
        self.assertEqual(self.inbox.connection.execute("SELECT COUNT(*) FROM outbox").fetchone()[0], 1)

    async def test_edit_of_initial_message_is_not_silent_forever(self):
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_INITIAL_BACKFILL":"10", "TELEGRAM_IMPORT_EDIT_SCAN":"2", "TELEGRAM_IMPORT_DEEP_SCAN":"2"}):
            await poll_once(FakeClient([message(1,"#анонс_зл 08.08 20:00")]),object(),-10042,self.inbox,self.notifier)
            edited = message(1,"#анонс_зл 08.08 21:00",datetime(2026,8,7,1,tzinfo=timezone.utc))
            await poll_once(FakeClient([edited]),object(),-10042,self.inbox,self.notifier)
        self.assertEqual(self.inbox.connection.execute("SELECT event_type FROM outbox WHERE status='PENDING'").fetchone()[0], "CANDIDATE")

    async def test_crashed_bootstrap_reuses_persisted_high_watermark(self):
        with self.inbox.transaction() as db:
            self.inbox.set_state(db,"initial_high_watermark",2)
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_INITIAL_BACKFILL":"10", "TELEGRAM_IMPORT_EDIT_SCAN":"2", "TELEGRAM_IMPORT_DEEP_SCAN":"2"}):
            await poll_once(FakeClient([message(1,"#анонс_зл 08.08 20:00"),message(2,"#анонс_лп 09.08 20:00"),message(3,"#анонс_зл 10.08 20:00")]),object(),-10042,self.inbox,self.notifier)
        self.assertEqual(self.inbox.get_state("initial_high_watermark"),"2")
        self.assertEqual(self.inbox.get_state("watermark"),"3")
        self.assertEqual(self.inbox.connection.execute("SELECT message_id FROM outbox WHERE status='PENDING'").fetchone()[0],3)


if __name__ == "__main__":
    unittest.main()
