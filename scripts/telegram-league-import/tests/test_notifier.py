import unittest
import json
import io
import urllib.error
from unittest.mock import patch

from telegram_import.config import NotifierConfig
from telegram_import.notifier import BotNotifier, render


class NotifierTest(unittest.TestCase):
    def test_copy_never_claims_mutation(self):
        text = render(json.dumps({"eventKey":"tg:-10042:12:r1:CANDIDATE","eventType":"CANDIDATE","classification":"RESULT","league":"ЛП","messageId":12,"revision":1,"sourceUrl":"https://t.me/polemica_closed_league/12"}))
        self.assertTrue(text.startswith("[SHADOW][NO PRODUCTION WRITE]"))
        self.assertIn("Найден кандидат", text)
        self.assertIn("не создана", text)
        self.assertNotIn("создана серия", text.casefold())
        self.assertIn("https://t.me/polemica_closed_league/12", text)

    def test_429_honors_retry_after_plus_jitter(self):
        notifier = BotNotifier(NotifierConfig(True, "secret-token", -10042, None))
        error = urllib.error.HTTPError("redacted", 429, "rate", {}, io.BytesIO(b'{"parameters":{"retry_after":7}}'))
        with patch("urllib.request.urlopen", side_effect=error), patch("random.random", return_value=0.25):
            result = notifier._send_sync("test")
        self.assertFalse(result.delivered)
        self.assertEqual(result.retry_after, 7.25)
        self.assertEqual(result.error, "HTTP 429")

    def test_403_is_terminal_and_safe(self):
        notifier = BotNotifier(NotifierConfig(True, "secret-token", -10042, None))
        error = urllib.error.HTTPError("redacted", 403, "forbidden", {}, io.BytesIO(b""))
        with patch("urllib.request.urlopen", side_effect=error):
            result = notifier._send_sync("test")
        self.assertTrue(result.terminal)
        self.assertNotIn("secret-token", result.error)


if __name__ == "__main__":
    unittest.main()
