from __future__ import annotations

import base64
import hashlib
import json
import random
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from .config import OcrConfig


MAX_EVIDENCE_LINES = 128
MAX_FULL_TEXT_BYTES = 64 * 1024
MAX_BACKEND_PAYLOAD_BYTES = 128 * 1024
MAX_PROVIDER_RESPONSE_BYTES = 2 * 1024 * 1024
SUPPORTED_FORMATS = {"JPEG": "image/jpeg", "PNG": "image/png"}
PROVIDER_MIME_TYPES = {mime_type: image_format for image_format, mime_type in SUPPORTED_FORMATS.items()}


@dataclass(frozen=True)
class MediaMetadata:
    mime_type: str
    byte_size: int
    width: int
    height: int
    sha256: str


@dataclass(frozen=True)
class OcrResult:
    status: str
    provider: str
    model: str
    language_codes: tuple[str, ...]
    full_text: str
    lines: tuple[dict[str, object], ...]
    checksum: str
    retryable: bool = False
    retry_after: float = 0
    error_code: str | None = None

    def evidence(self) -> dict[str, object]:
        return {
            "status": self.status,
            "provider": self.provider,
            "model": self.model,
            "languageCodes": list(self.language_codes),
            "checksum": self.checksum,
            "fullText": self.full_text,
            "lines": list(self.lines),
            "errorCode": self.error_code,
        }


def inspect_media(path: Path, config: OcrConfig) -> MediaMetadata:
    from PIL import Image, UnidentifiedImageError

    size = path.stat().st_size
    if size <= 0 or size > config.max_media_bytes:
        raise ValueError("MEDIA_SIZE_UNSUPPORTED")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    try:
        with Image.open(path) as image:
            image_format = (image.format or "").upper()
            mime = SUPPORTED_FORMATS.get(image_format)
            if mime is None:
                raise ValueError("MEDIA_MIME_UNSUPPORTED")
            width, height = image.size
            if width <= 0 or height <= 0 or width * height > config.max_pixels:
                raise ValueError("MEDIA_DIMENSIONS_UNSUPPORTED")
            image.verify()
    except UnidentifiedImageError as exc:
        raise ValueError("MEDIA_INVALID") from exc
    return MediaMetadata(mime, size, width, height, digest.hexdigest())


def ocr_checksum(
    provider: str,
    model: str,
    language_codes: tuple[str, ...],
    full_text: str,
    lines: tuple[dict[str, object], ...],
) -> str:
    material = ["v1", provider, model, ",".join(language_codes), full_text]
    for line in lines:
        points = ";".join(f"{point['x']},{point['y']}" for point in line["boundingBox"])
        material.append(f"{line['text']}|{points}")
    return hashlib.sha256("\n".join(material).encode("utf-8")).hexdigest()


def terminal_ocr(status: str, config: OcrConfig, error_code: str) -> OcrResult:
    checksum = ocr_checksum("YANDEX_VISION", config.model, config.language_codes, "", ())
    return OcrResult(
        status=status,
        provider="YANDEX_VISION",
        model=config.model,
        language_codes=config.language_codes,
        full_text="",
        lines=(),
        checksum=checksum,
        error_code=error_code,
    )


def evidence_hash(content_hash: str, media: dict[str, object] | None) -> str:
    ocr = media.get("ocr") if media else None
    material = [
        "v1",
        content_hash,
        _canonical(media, "telegramMediaId"),
        _canonical(media, "groupedId"),
        _canonical(media, "mimeType"),
        _canonical(media, "byteSize"),
        _canonical(media, "width"),
        _canonical(media, "height"),
        _canonical(media, "sha256"),
        _canonical(ocr, "status", "NONE"),
        _canonical(ocr, "provider"),
        _canonical(ocr, "model"),
        ",".join(ocr.get("languageCodes", [])) if ocr and ocr.get("languageCodes") else "-",
        _canonical(ocr, "checksum"),
    ]
    return hashlib.sha256("\n".join(material).encode("utf-8")).hexdigest()


def _canonical(value: dict[str, object] | None, key: str, missing: str = "-") -> str:
    if not value or value.get(key) is None:
        return missing
    return str(value[key])


class YandexVisionClient:
    def __init__(self, config: OcrConfig):
        self.config = config

    async def recognize(self, path: Path, metadata: MediaMetadata) -> OcrResult:
        import asyncio

        return await asyncio.to_thread(self._recognize_sync, path, metadata)

    def _recognize_sync(self, path: Path, metadata: MediaMetadata) -> OcrResult:
        body = json.dumps(
            {
                "content": base64.b64encode(path.read_bytes()).decode("ascii"),
                "mimeType": PROVIDER_MIME_TYPES[metadata.mime_type],
                "languageCodes": list(self.config.language_codes),
                "model": self.config.model,
            },
            separators=(",", ":"),
        ).encode("utf-8")
        request = urllib.request.Request(
            self.config.api_url,
            data=body,
            method="POST",
            headers={
                "Authorization": f"Api-Key {self.config.api_key}",
                "x-folder-id": self.config.folder_id,
                "x-data-logging-enabled": "false",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self.config.timeout_seconds) as response:
                raw_response = response.read(MAX_PROVIDER_RESPONSE_BYTES + 1)
                if len(raw_response) > MAX_PROVIDER_RESPONSE_BYTES:
                    return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_TOO_LARGE")
                payload = json.loads(raw_response)
            return self._parse(payload)
        except urllib.error.HTTPError as exc:
            if exc.code == 429 or 500 <= exc.code < 600:
                try:
                    retry = int(exc.headers.get("Retry-After", "2")) if exc.headers else 2
                except ValueError:
                    retry = 2
                return self._retry("PROVIDER_UNAVAILABLE", retry + random.random())
            return terminal_ocr("FAILED", self.config, f"PROVIDER_HTTP_{exc.code}")
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            return self._retry("PROVIDER_UNAVAILABLE", 2 + random.random() * 3)

    def _parse(self, payload: object) -> OcrResult:
        if not isinstance(payload, dict):
            return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
        result = payload.get("result")
        if not isinstance(result, dict):
            return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
        annotation = result.get("textAnnotation")
        if not isinstance(annotation, dict):
            return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
        full_text = str(annotation.get("fullText") or "")
        blocks = annotation.get("blocks") or []
        if not isinstance(blocks, list):
            return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
        lines: list[dict[str, object]] = []
        for block in blocks:
            if not isinstance(block, dict):
                return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
            for line in block.get("lines") or []:
                if not isinstance(line, dict):
                    return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                points = []
                bounding_box = line.get("boundingBox") or {}
                if not isinstance(bounding_box, dict):
                    return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                vertices = bounding_box.get("vertices") or []
                if not isinstance(vertices, list) or len(vertices) > 8:
                    return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                for vertex in vertices:
                    if not isinstance(vertex, dict):
                        return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                    try:
                        x = int(vertex.get("x", 0))
                        y = int(vertex.get("y", 0))
                    except (TypeError, ValueError):
                        return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                    if x < 0 or y < 0:
                        return terminal_ocr("FAILED", self.config, "PROVIDER_RESPONSE_INVALID")
                    points.append({"x": x, "y": y})
                line_text = str(line.get("text") or "")
                if len(line_text) > 256:
                    return terminal_ocr("FAILED", self.config, "EVIDENCE_TOO_LARGE")
                lines.append({"text": line_text, "boundingBox": points})
        if not full_text.strip():
            return terminal_ocr("FAILED", self.config, "OCR_EMPTY")
        if len(lines) > MAX_EVIDENCE_LINES or len(full_text.encode("utf-8")) > MAX_FULL_TEXT_BYTES:
            return terminal_ocr("FAILED", self.config, "EVIDENCE_TOO_LARGE")
        line_tuple = tuple(lines)
        checksum = ocr_checksum("YANDEX_VISION", self.config.model, self.config.language_codes, full_text, line_tuple)
        return OcrResult(
            status="SUCCESS",
            provider="YANDEX_VISION",
            model=self.config.model,
            language_codes=self.config.language_codes,
            full_text=full_text,
            lines=line_tuple,
            checksum=checksum,
        )

    def _retry(self, error_code: str, retry_after: float) -> OcrResult:
        result = terminal_ocr("FAILED", self.config, error_code)
        return OcrResult(**{**result.__dict__, "retryable": True, "retry_after": retry_after})
