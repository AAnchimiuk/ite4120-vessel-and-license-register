# VESSELS-US-004 · Retire a vessel

As a maritime administration officer (Transpordiamet / Veeteede Amet) I want to retire a vessel that has been scrapped, lost or moved to a foreign register, so that the active register only shows vessels that can legally operate under the Estonian flag — while its records stay available for audit.

## Context

Vessels leave the register for good reasons: scrapped, lost at sea, or re-flagged to another country. Their records must not disappear, because past ownership and licenses may still be needed by inspectors or courts. Retirement is therefore a soft delete: the vessel is marked retired with a date and reason, hidden from the active register, and its registry code becomes free for reuse (see VESSELS-US-001). A retired vessel cannot operate, so any license still valid at retirement ends with it. One source of truth per fact.

## Acceptance criteria

- An officer can retire an active vessel with a retirement date and a reason: *Scrapped*, *Lost*, *Re-flagged abroad* or *Other* (with a free-text note).
- A retirement date in the future, an unknown reason, or retiring an already retired vessel is refused with a validation error (400).
- On retirement, every license that is still valid is revoked as of the retirement date with the reason "Vessel retired".
- Retired vessels are hidden from the register list by default and shown when the officer filters for retired vessels.
- A retired vessel's detail view still shows its data, ownership history and license history, clearly marked as retired with the date and reason.
- A retired vessel cannot receive new licenses or ownership transfers.
- After retirement, the vessel's registry code can be used to register a new vessel.