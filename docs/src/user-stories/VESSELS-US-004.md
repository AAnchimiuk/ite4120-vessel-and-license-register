# VESSELS-US-004 · Retire a vessel

> **VESSELS-US-004** · state **Proposed** · traces to [VESSELS.01](../specifications/VESSELS.01-vessel-register.md)

**As** a maritime administration officer (Transpordiamet / Veeteede Amet)  
**I want** to retire a vessel that has been scrapped, lost or moved to a foreign register  
**so that** the active register only shows vessels that can legally operate under the Estonian flag — while its records stay available for audit.

## Context

Vessels leave the register for good reasons: scrapped, lost at sea, or re-flagged to another country. Their records must not disappear, because past ownership and licenses may still be needed by inspectors or courts. Retirement is therefore a recorded soft delete: the vessel is marked retired with a date, a reason, and an explanatory note, hidden from the active register, and its registry code becomes free for reuse (see [VESSELS-US-001](VESSELS-US-001.md)). A retired vessel cannot operate, so any license still valid at retirement ends with it. One source of truth per fact.

## Acceptance criteria

- An officer can retire an active vessel with a retirement date and a reason: *Scrapped*, *Lost*, *Re-flagged abroad*, or *Other* (with a mandatory explanatory note).
- A retirement date in the future, an unknown reason, or an empty note when reason is *Other* is refused with a validation error (400).
- Retiring an already retired vessel is refused with a conflict error (409 Conflict).
- On retirement, every license that is currently valid (`VALID` or `EXPIRING_SOON`) is automatically revoked as of the retirement date with the reason "Vessel retired".
- Retired vessels are hidden from the register list by default and shown when the officer filters for retired vessels.
- A retired vessel's detail view still displays its full data, ownership history, and license history, clearly marked with a retirement banner showing the date, reason, and note.
- A retired vessel is frozen: it cannot be updated, and cannot receive new licenses or ownership transfers (409 Conflict).
- After retirement, the vessel's registry code is freed and can be reused to register a new active vessel.
