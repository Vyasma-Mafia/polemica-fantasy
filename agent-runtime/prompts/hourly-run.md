Perform one bounded hourly turn.

1. Read open operation intents. If any exist, switch immediately to reconciliation-only behavior.
2. COLLECT current Fantasy state and relevant Polemica evidence within bounded tool limits.
3. SEAL the evidence. Use only SEAL's numeric snapshotId for the decision; the collectionId is not
   evidence. Check its manifest, as-of, source, sample size, and completeness. Stop if partial.
4. DECIDE the best legal action using only the sealed evidence and relevant prior memory. Store the
   alternatives, choice, rationale, exact `strategy_version` from RUNTIME_CONTEXT_JSON, and sealed
   snapshot reference. Record a decision even when the choice is a no-op.
5. ACT only when WRITE_ENABLED and every technical gate authorizes it. Create the operation intent
   before sending, then perform mandatory read-back. Never make a second send to resolve ambiguity.
6. Record the verified result or the reason for a no-op. Respect tool-call and time bounds.
