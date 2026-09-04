ALTER TABLE runs ADD COLUMN strategy_version TEXT REFERENCES strategy_versions(version);
CREATE INDEX IF NOT EXISTS idx_runs_strategy_version ON runs(strategy_version);
CREATE INDEX IF NOT EXISTS idx_decisions_run_strategy ON decisions(run_id, strategy_version);
