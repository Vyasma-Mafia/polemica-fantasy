import hashlib
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

from telegram_import.config import OcrConfig
from telegram_import.ocr import MediaMetadata, YandexVisionClient, evidence_hash, inspect_media, ocr_checksum, terminal_ocr


class _Response:
    def __init__(self, payload):
        self.payload = payload
    def __enter__(self): return self
    def __exit__(self, *_): return False
    def read(self, *_): return json.dumps(self.payload).encode()


class OcrEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.config = OcrConfig(True, "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText", "secret", "folder")

    def test_checksum_and_evidence_hash_follow_backend_canonical_contract(self):
        self.assertEqual(
            evidence_hash("a" * 64, None),
            "26079a3500abe95d1f50f0e6b6ffd20727345a7c3e5b32dc6e284c34b11d31d3",
        )
        lines = ({"text":"Player","boundingBox":[{"x":1,"y":2},{"x":3,"y":4}]},)
        checksum = ocr_checksum("YANDEX_VISION", "page", ("ru","en"), "Player", lines)
        expected_checksum = hashlib.sha256(
            "v1\nYANDEX_VISION\npage\nru,en\nPlayer\nPlayer|1,2;3,4".encode()
        ).hexdigest()
        self.assertEqual(checksum, expected_checksum)
        media = {
            "telegramMediaId":"9001", "groupedId":None, "mimeType":"image/jpeg",
            "byteSize":123, "width":100, "height":200, "sha256":"b" * 64,
            "ocr":{"status":"SUCCESS","provider":"YANDEX_VISION","model":"page","languageCodes":["ru","en"],"checksum":checksum},
        }
        expected_material = "\n".join([
            "v1", "a" * 64, "9001", "-", "image/jpeg", "123", "100", "200", "b" * 64,
            "SUCCESS", "YANDEX_VISION", "page", "ru,en", checksum,
        ])
        self.assertEqual(evidence_hash("a" * 64, media), hashlib.sha256(expected_material.encode()).hexdigest())

        terminal = terminal_ocr("FAILED", self.config, "DOWNLOAD_FAILED")
        terminal_media = {
            "telegramMediaId":"photo-1", "groupedId":None, "mimeType":None,
            "byteSize":None, "width":None, "height":None, "sha256":None,
            "ocr":terminal.evidence(),
        }
        self.assertEqual(
            evidence_hash("a" * 64, terminal_media),
            "14d53717b95e09bf83bab76f9b502e2bca07ab1b850b84ee22c3fd0f629fecf2",
        )

    def test_failed_and_unsupported_evidence_have_checksum(self):
        for status in ("FAILED", "UNSUPPORTED"):
            result = terminal_ocr(status, self.config, "TEST")
            self.assertEqual(result.status, status)
            self.assertEqual(len(result.checksum), 64)
            self.assertEqual(result.full_text, "")
            self.assertEqual(result.lines, ())

    def test_yandex_response_preserves_line_order_and_bounding_boxes(self):
        payload = {"result":{"textAnnotation":{"fullText":"One\nTwo","blocks":[{"lines":[
            {"text":"One","boundingBox":{"vertices":[{"x":"1","y":"2"}]}},
            {"text":"Two","boundingBox":{"vertices":[{"x":3,"y":4}]}},
        ]}]}}}
        result = YandexVisionClient(self.config)._parse(payload)
        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual([line["text"] for line in result.lines], ["One", "Two"])
        self.assertEqual(result.lines[0]["boundingBox"], [{"x":1,"y":2}])

    def test_empty_response_is_terminal_failed_not_silent(self):
        result = YandexVisionClient(self.config)._parse({"result":{"textAnnotation":{}}})
        self.assertEqual((result.status, result.retryable, result.error_code), ("FAILED", False, "OCR_EMPTY"))

    def test_malformed_response_is_terminal_failed_not_worker_crash(self):
        result = YandexVisionClient(self.config)._parse({"result": []})
        self.assertEqual((result.status, result.error_code), ("FAILED", "PROVIDER_RESPONSE_INVALID"))
        root_array = YandexVisionClient(self.config)._parse([])
        self.assertEqual((root_array.status, root_array.error_code), ("FAILED", "PROVIDER_RESPONSE_INVALID"))

    def test_provider_shape_is_bounded_to_backend_contract(self):
        long_line = {"result":{"textAnnotation":{"fullText":"x","blocks":[{"lines":[
            {"text":"x" * 257,"boundingBox":{"vertices":[]}},
        ]}]}}}
        result = YandexVisionClient(self.config)._parse(long_line)
        self.assertEqual((result.status, result.error_code), ("FAILED", "EVIDENCE_TOO_LARGE"))
        too_many_vertices = {"result":{"textAnnotation":{"fullText":"x","blocks":[{"lines":[
            {"text":"x","boundingBox":{"vertices":[{"x":1,"y":1}] * 9}},
        ]}]}}}
        result = YandexVisionClient(self.config)._parse(too_many_vertices)
        self.assertEqual((result.status, result.error_code), ("FAILED", "PROVIDER_RESPONSE_INVALID"))

    def test_yandex_request_is_page_ru_en_and_uses_scoped_headers(self):
        response = _Response({"result":{"textAnnotation":{"fullText":"Player","blocks":[]}}})
        metadata = MediaMetadata("image/png", 3, 1, 1, "a" * 64)
        with tempfile.TemporaryDirectory() as temporary:
            image = Path(temporary) / "image.png"
            image.write_bytes(b"png")
            with patch("urllib.request.urlopen", return_value=response) as urlopen:
                result = YandexVisionClient(self.config)._recognize_sync(image, metadata)
        request = urlopen.call_args.args[0]
        body = json.loads(request.data)
        self.assertEqual(request.full_url, "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText")
        self.assertEqual(request.get_header("Authorization"), "Api-Key secret")
        self.assertEqual(request.get_header("X-folder-id"), "folder")
        self.assertEqual(request.get_header("X-data-logging-enabled"), "false")
        self.assertEqual((body["model"], body["languageCodes"], body["mimeType"]), ("page", ["ru","en"], "PNG"))
        self.assertEqual(result.status, "SUCCESS")

    def test_provider_429_is_retryable_and_other_4xx_is_terminal(self):
        metadata = MediaMetadata("image/png", 3, 1, 1, "a" * 64)
        with tempfile.TemporaryDirectory() as temporary:
            image = Path(temporary) / "image.png"
            image.write_bytes(b"png")
            for code, retryable, error in ((429, True, "PROVIDER_UNAVAILABLE"), (400, False, "PROVIDER_HTTP_400")):
                failure = urllib.error.HTTPError(self.config.api_url, code, "test", {}, None)
                with self.subTest(code=code), patch("urllib.request.urlopen", side_effect=failure):
                    result = YandexVisionClient(self.config)._recognize_sync(image, metadata)
                self.assertEqual((result.retryable, result.error_code), (retryable, error))

    def test_media_inspection_accepts_png_and_enforces_byte_limit(self):
        from PIL import Image

        with tempfile.TemporaryDirectory() as temporary:
            image = Path(temporary) / "image.png"
            Image.new("RGB", (2, 3), "white").save(image, "PNG")
            metadata = inspect_media(image, self.config)
            self.assertEqual((metadata.mime_type, metadata.width, metadata.height), ("image/png", 2, 3))
            tiny_limit = OcrConfig(True, self.config.api_url, "secret", "folder", max_media_bytes=1)
            with self.assertRaisesRegex(ValueError, "MEDIA_SIZE_UNSUPPORTED"):
                inspect_media(image, tiny_limit)


if __name__ == "__main__":
    unittest.main()
