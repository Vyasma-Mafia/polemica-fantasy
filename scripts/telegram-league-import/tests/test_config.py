import os
import unittest
from unittest.mock import patch

from telegram_import.config import DeliveryMode, delivery_mode, notifier_config, ocr_config, require_shadow_mode


class ConfigTest(unittest.TestCase):
    def test_shadow_mode_is_exact(self):
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_MODE":"shadow"}, clear=True):
            with self.assertRaises(SystemExit):
                require_shadow_mode()
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_MODE":"SHADOW"}, clear=True):
            require_shadow_mode()

    def test_notifier_is_disabled_by_default_even_with_no_credentials(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(notifier_config().enabled)

    def test_notifier_requires_pair_and_explicit_enable(self):
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN":"secret"}, clear=True):
            with self.assertRaises(SystemExit):
                notifier_config()
        environment = {"TELEGRAM_IMPORT_OPERATOR_NOTIFICATIONS_ENABLED":"true", "TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN":"secret", "TELEGRAM_IMPORT_OPERATOR_CHAT_ID":"-10042"}
        with patch.dict(os.environ, environment, clear=True):
            self.assertTrue(notifier_config().enabled)

    def test_thread_alone_is_invalid(self):
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_OPERATOR_THREAD_ID":"42"}, clear=True):
            with self.assertRaises(SystemExit):
                notifier_config()

    def test_delivery_mode_is_exact_and_backend_requires_https_credentials(self):
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_DELIVERY_MODE":"backend"}, clear=True):
            with self.assertRaises(SystemExit):
                delivery_mode()
        environment = {
            "TELEGRAM_IMPORT_DELIVERY_MODE":"BACKEND",
            "TELEGRAM_IMPORT_BACKEND_INGEST_URL":"https://fantasy.example/api/v1/internal/telegram-league-import/events",
            "TELEGRAM_IMPORT_BACKEND_INGEST_KEY_ID":"k1",
            "TELEGRAM_IMPORT_BACKEND_INGEST_SECRET":"secret",
        }
        with patch.dict(os.environ, environment, clear=True):
            self.assertEqual(delivery_mode(), DeliveryMode.BACKEND)

    def test_backend_mode_rejects_plain_http(self):
        environment = {
            "TELEGRAM_IMPORT_DELIVERY_MODE":"BACKEND",
            "TELEGRAM_IMPORT_BACKEND_INGEST_URL":"http://backend/events",
            "TELEGRAM_IMPORT_BACKEND_INGEST_KEY_ID":"k1",
            "TELEGRAM_IMPORT_BACKEND_INGEST_SECRET":"secret",
        }
        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaises(SystemExit):
                delivery_mode()

    def test_backend_mode_rejects_legacy_bot_credentials(self):
        environment = {
            "TELEGRAM_IMPORT_DELIVERY_MODE":"BACKEND",
            "TELEGRAM_IMPORT_BACKEND_INGEST_URL":"https://fantasy.example/events",
            "TELEGRAM_IMPORT_BACKEND_INGEST_KEY_ID":"k1",
            "TELEGRAM_IMPORT_BACKEND_INGEST_SECRET":"secret",
            "TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN":"legacy-token",
            "TELEGRAM_IMPORT_OPERATOR_CHAT_ID":"-10042",
        }
        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaises(SystemExit):
                delivery_mode()

    def test_ocr_is_default_off_and_backend_only(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(ocr_config(DeliveryMode.BACKEND).enabled)
        with patch.dict(os.environ, {"TELEGRAM_IMPORT_OCR_ENABLED":"true"}, clear=True):
            with self.assertRaises(SystemExit):
                ocr_config(DeliveryMode.DIRECT)

    def test_ocr_requires_scoped_credentials_and_exact_yandex_endpoint(self):
        environment = {
            "TELEGRAM_IMPORT_OCR_ENABLED":"true",
            "TELEGRAM_IMPORT_OCR_API_KEY":"scoped-key",
            "TELEGRAM_IMPORT_OCR_FOLDER_ID":"folder",
        }
        with patch.dict(os.environ, environment, clear=True):
            config = ocr_config(DeliveryMode.BACKEND)
            self.assertTrue(config.enabled)
            self.assertEqual(config.model, "page")
            self.assertEqual(config.language_codes, ("ru", "en"))
        with patch.dict(os.environ, {**environment, "TELEGRAM_IMPORT_OCR_API_URL":"https://evil.test/collect"}, clear=True):
            with self.assertRaises(SystemExit):
                ocr_config(DeliveryMode.BACKEND)


if __name__ == "__main__":
    unittest.main()
