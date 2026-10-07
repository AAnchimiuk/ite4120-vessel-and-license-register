# VESSELS-US-001 · Register and look up vessels and licenses

> **VESSELS-US-001** · state **Proposed** · traces to [VESSELS.01](../specifications/VESSELS.01-vessel-register.md)

**As** a maritime administration officer (Transpordiamet / Veeteede Amet)
**I want** a register of vessels with their owners and maritime licenses
**so that** I can verify whether a vessel is legally registered, who owns it, and
whether its operating and safety licenses are active and valid — without consulting multiple separate authorities.

## Context

The Estonian maritime administration maintains official state records of all water craft,
recreational vessels, and commercial ships.
The officer registers a vessel with a unique national or international registry code.
The owner of the vessel is **not** stored as free text: the register stores only the owner's
personal identification code (`isikukood` for private persons) and resolves the owner's
identity and registered address live from the Estonian population registry over X-Road
(imitated registry adapter in local development). One source of truth per fact.

Furthermore, each vessel may hold one or more maritime licenses (e.g. navigation permit,
commercial fishing license, passenger transport certification) which have clear validity periods.

## Acceptance criteria

- A vessel can be registered with a unique registry code, name, vessel type, and optional build year, home port, gross tonnage, and owner code.
- A registry code identifies exactly one **active** vessel; codes of retired (soft-deleted) vessels may be reused.
- Obvious nonsense is refused with a clear validation error (400): unknown vessel type, build year in the future, malformed personal code (must be exactly 11 digits).
- The vessel register is searchable by name or registry code and filterable by vessel type.
- For a vessel with an owner, the officer sees the owner's current full name and address, fetched live from the external registry adapter — never stored locally.
- Maritime licenses can be viewed and added for each vessel, with a license number, license type, issue date, and expiration date.
- A license whose expiration date is prior to its issue date is refused.

