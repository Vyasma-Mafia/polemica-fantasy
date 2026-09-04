from __future__ import annotations

import json
import sys
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2] / "src"))

from polemica_agent.research_mcp.cache import RawPayloadCache
from polemica_agent.research_mcp.errors import ContractError, SnapshotSealedError
from polemica_agent.research_mcp.snapshots import InMemorySnapshotJournal, SnapshotCoordinator


FIXED = datetime(2026, 9, 4, 12, 0, tzinfo=timezone.utc)


class CacheAndSnapshotTest(unittest.TestCase):
    def test_cache_deduplicates_identical_and_versions_correction(self) -> None:
        with tempfile.TemporaryDirectory() as root:
            cache = RawPayloadCache(root, clock=lambda: FIXED)
            first = cache.store(source="match", object_id="7", source_version=1, payload={"id": 7, "score": 1})
            replay = cache.store(source="match", object_id="7", source_version=1, payload={"score": 1, "id": 7})
            corrected = cache.store(source="match", object_id="7", source_version=1, payload={"id": 7, "score": 2})

            self.assertEqual(first.payload_hash, replay.payload_hash)
            self.assertNotEqual(first.payload_hash, corrected.payload_hash)
            self.assertEqual(2, len(cache.versions(source="match", object_id="7", source_version=1)))
            self.assertEqual(1, first.correction_index)
            self.assertEqual(2, corrected.correction_index)
            self.assertEqual({"id": 7, "score": 1}, cache.load(first.payload_hash))
            self.assertEqual([], list(Path(root).rglob(".cache-*")))

    def test_cache_detects_blob_tampering_on_load_and_reuse(self) -> None:
        with tempfile.TemporaryDirectory() as root:
            cache = RawPayloadCache(root, clock=lambda: FIXED)
            record = cache.store(source="match", object_id="7", payload={"id": 7})
            Path(record.blob_path).write_text('{"id":8}', encoding="utf-8")
            with self.assertRaisesRegex(ContractError, "hash mismatch"):
                cache.load(record.payload_hash)
            with self.assertRaisesRegex(ContractError, "hash mismatch"):
                cache.store(source="match", object_id="7", payload={"id": 7})

    def test_sealed_snapshot_rejects_subsequent_attachment(self) -> None:
        with tempfile.TemporaryDirectory() as root:
            cache = RawPayloadCache(root, clock=lambda: FIXED)
            record = cache.store(source="match", object_id="7", payload={"id": 7})
            coordinator = SnapshotCoordinator(InMemorySnapshotJournal(), clock=lambda: FIXED)
            snapshot = coordinator.begin(str(uuid.uuid4()))
            coordinator.attach(snapshot.snapshot_id, record)
            sealed = coordinator.seal(snapshot.snapshot_id)

            self.assertEqual("SEALED", sealed.state)
            with self.assertRaises(SnapshotSealedError):
                coordinator.attach(snapshot.snapshot_id, record)
            with self.assertRaises(SnapshotSealedError):
                coordinator.require_collecting(snapshot.snapshot_id)


if __name__ == "__main__":
    unittest.main()
