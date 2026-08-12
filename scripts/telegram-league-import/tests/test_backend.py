import hashlib
import hmac
import unittest
import urllib.error
from email.message import Message
from unittest.mock import patch

from telegram_import.backend import BackendIngestClient
from telegram_import.config import BackendDeliveryConfig


class _Response:
    status = 200
    def __enter__(self): return self
    def __exit__(self, *_): return False


class BackendIngestClientTest(unittest.TestCase):
    def test_signature_binds_key_delivery_timestamp_and_exact_body(self):
        client = BackendIngestClient(BackendDeliveryConfig("https://example.test/events", "key-1", "secret"))
        with patch("telegram_import.backend.datetime") as clock, patch("urllib.request.urlopen", return_value=_Response()) as urlopen:
            clock.now.return_value.timestamp.return_value = 1234567890
            result = client._send_sync("11111111-1111-1111-1111-111111111111", '{"rawText":"ЛП"}')
        self.assertTrue(result.delivered)
        request = urlopen.call_args.args[0]
        expected = hmac.new(
            b"secret",
            b"key-1\n11111111-1111-1111-1111-111111111111\n1234567890\n" + b'{"rawText":"\xd0\x9b\xd0\x9f"}',
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual(request.headers["X-league-import-signature"], expected)

    def test_429_and_5xx_are_retried_but_other_4xx_is_terminal(self):
        client = BackendIngestClient(BackendDeliveryConfig("https://example.test/events", "key-1", "secret"))
        headers = Message()
        headers["Retry-After"] = "7"
        cases = (
            (urllib.error.HTTPError(client.config.url, 429, "rate", headers, None), False, False, 7),
            (urllib.error.HTTPError(client.config.url, 503, "down", Message(), None), False, False, 5),
            (urllib.error.HTTPError(client.config.url, 400, "bad", Message(), None), False, True, 0),
        )
        for failure, delivered, terminal, minimum_retry in cases:
            with self.subTest(code=failure.code), patch("urllib.request.urlopen", side_effect=failure):
                result = client._send_sync("11111111-1111-1111-1111-111111111111", "{}")
                self.assertEqual(result.delivered, delivered)
                self.assertEqual(result.terminal, terminal)
                self.assertGreaterEqual(result.retry_after, minimum_retry)

    def test_network_failure_is_retried(self):
        client = BackendIngestClient(BackendDeliveryConfig("https://example.test/events", "key-1", "secret"))
        with patch("urllib.request.urlopen", side_effect=urllib.error.URLError("offline")):
            result = client._send_sync("11111111-1111-1111-1111-111111111111", "{}")
        self.assertFalse(result.delivered)
        self.assertFalse(result.terminal)
        self.assertGreaterEqual(result.retry_after, 5)


if __name__ == "__main__":
    unittest.main()
