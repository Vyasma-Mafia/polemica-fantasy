from concurrent.futures import ThreadPoolExecutor

import pytest

from polemica_agent.memory_mcp.service import MemoryService
from polemica_agent.mcp_runtime.memory_tools import MemoryTools


def test_notes_append_preserves_prior_text_and_requires_running_run(tmp_path):
    service = MemoryService(tmp_path / "state.sqlite3")
    tools = MemoryTools(service)
    assert tools.read_developer_notes() == ""
    with pytest.raises(ValueError, match="running run"):
        tools.append_developer_note("absent", "Issue", "Details")
    service.store.start_run(run_id="run", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda n: tools.append_developer_note("run", f"Issue {n}", f"Details {n}"), range(8)))
    text = tools.read_developer_notes()
    for n in range(8):
        assert text.count(f"Details {n}") == 1
    assert (tmp_path / "DEVELOPER-NOTES.md").stat().st_mode & 0o777 == 0o600
    service.close()


def test_notes_reject_symlink_and_oversized_note(tmp_path):
    from polemica_agent.memory_mcp.developer_notes import DeveloperNotes
    notes = DeveloperNotes(tmp_path)
    with pytest.raises(ValueError):
        notes.append("run", "Title", "x" * 4001)
    target = tmp_path / "other.md"
    target.write_text("keep")
    notes.path.symlink_to(target)
    with pytest.raises(OSError):
        notes.append("run", "Title", "body")
    assert target.read_text() == "keep"


def test_compact_notes_head_tail_cursor_and_complete_pagination(tmp_path):
    from polemica_agent.memory_mcp.developer_notes import DeveloperNotes
    notes = DeveloperNotes(tmp_path)
    for n in range(12):
        notes.append("run", f"Issue {n}", f"body-{n} " + "я" * 900)
    original = notes.path.read_text()
    compact = notes.read()
    assert "Issue 0" in compact and "Issue 11" in compact
    assert "truncated: true" in compact and len(compact) < 6500
    digest = compact.splitlines()[0].split(": ")[1]
    assert notes.read(known_hash=digest).endswith("unchanged: true")
    pages = []
    offset = 0
    while True:
        page = notes.read(compact=False, offset=offset, limit=1000)
        header, body = page.split("\n\n", 1)
        pages.append(body)
        following = next(line.split(": ")[1] for line in header.splitlines() if line.startswith("nextOffset:"))
        if following == "none":
            break
        offset = int(following)
    assert "".join(pages) == original
    notes.append("run", "New", "fresh")
    assert "unchanged: true" not in notes.read(known_hash=digest)


def test_memory_compact_does_not_change_stored_records():
    from types import SimpleNamespace
    row = {"decisionId": 9, "runId": "r", "subjectType": "league", "subjectId": "5",
           "decidedAt": "time", "choice": {"ids": [1, 2]}, "outcome": {"score": 4},
           "rationale": "r" * 8000, "alternatives": ["a" * 9000]}
    service = SimpleNamespace(get_relevant_memory=lambda **kwargs: [row])
    tools = MemoryTools(service)
    result = tools.get_relevant_memory()[0]
    assert result["choice"] == row["choice"] and result["outcome"] == row["outcome"]
    assert result["truncatedFields"] == ["rationale", "alternatives"]
    assert len(str(result)) < 1500
    assert tools.get_relevant_memory(compact=False)[0] is row
    assert len(row["rationale"]) == 8000
