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
