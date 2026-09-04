from __future__ import annotations

import math

import pytest

from polemica_agent.common.canonical import REDACTED, canonical_json, payload_hash, redact


def test_canonical_json_is_stable() -> None:
    left = {"ю": [3, 2], "a": {"z": True, "b": None}}
    right = {"a": {"b": None, "z": True}, "ю": [3, 2]}
    assert canonical_json(left) == canonical_json(right)
    assert payload_hash(left) == payload_hash(right)


def test_canonical_json_rejects_nan() -> None:
    with pytest.raises(ValueError):
        canonical_json({"score": math.nan})


def test_redaction_is_recursive_and_catches_auth_values() -> None:
    source = {
        "Authorization": "Bearer abc",
        "nested": [{"api_token": "abc", "safe": "ok"}],
        "text": "Basic Zm9vOmJhcg==",
    }
    assert redact(source) == {
        "Authorization": REDACTED,
        "nested": [{"api_token": REDACTED, "safe": "ok"}],
        "text": REDACTED,
    }
