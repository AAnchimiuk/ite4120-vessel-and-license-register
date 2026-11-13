# VESSELS-US-003 · Transfer vessel ownership

> **VESSELS-US-003** · state **Proposed** · traces to [VESSELS.01](../specifications/VESSELS.01-vessel-register.md)

**As** a maritime administration officer (Transpordiamet / Veeteede Amet)  
**I want** to record a change of a vessel's owner and keep the history of past owners  
**so that** I can always tell who owns a vessel today and who owned it on any past date — without reconstructing it from sale documents.

## Context

Vessels change hands through sale, inheritance or company restructuring. The register still stores only the owner's personal identification code (`isikukood`), never their name or address. When ownership changes, the new code is checked live against the Estonian population registry over X-Road (imitated registry adapter in local development) before it is accepted. The previous owner is not overwritten: each ownership is kept as a period with a start and end date, so the history is preserved. Names and addresses in the history are resolved live as well. One source of truth per fact.

## Acceptance criteria

- An officer can transfer an active vessel to a new owner by entering the new owner's personal code and a transfer date.
- The new personal code is validated live against the population registry; a malformed code (not exactly 11 digits) or a code the registry does not recognise is refused with a validation error (400).
- If the population registry cannot be reached, the transfer is refused with a service error (502 Bad Gateway) and nothing is saved (fail-closed policy).
- Transferring to the current owner, or with a transfer date in the future or before the current ownership started, is refused with a validation error (400).
- On transfer, the current ownership ends on the transfer date and the new ownership starts on it.
- The vessel's detail view shows its ownership history — owner name, personal code, start date and end date — newest first, with names fetched live and never stored locally.
- A retired (soft-deleted) vessel cannot be transferred; attempts are refused with a conflict error (409).
