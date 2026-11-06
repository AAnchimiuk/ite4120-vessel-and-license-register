# VESSELS.03 · Vessel ownership transfer

> **VESSELS.03** · state **Proposed** · traces from [VESSELS-US-003](https://github.com/AAnchimiuk/ite4120-vessel-and-license-register/blob/main/docs/src/user-stories/VESSELS-US-003.md) · builds on [VESSELS.01](VESSELS.01-vessel-register.md) · source `backend/src/main/java/ee/taltech/ite4120/vessels/`, `backend/src/main/resources/vessels/db/changelog/`, `frontend/src/pages/vessels/`

## Description

Records changes of a vessel's owner as dated ownership periods, so the current owner and every past owner can be answered. Only personal identification codes and dates are stored; names and addresses — current and historical — are resolved live through the owner-registry adapter. A new owner's code must be confirmed by the registry before a transfer is accepted. Excluded: transfers between companies (registry codes), co-ownership and shares, sale prices and documents.

## Data model

New table `vessels.vessel_ownership`; `vessels.vessel.owner_isikukood` stays as the **current owner** and is updated in the same transaction as the ownership rows.

| Table                      | Field             | Type   | Rules                                                                           |
|----------------------------|-------------------|--------|---------------------------------------------------------------------------------|
| `vessels.vessel_ownership` | `id` PK           | bigint | from `core.seq_id`                                                              |
|                            | `vessel_id`       | bigint | required; FK → `vessels.vessel`                                                 |
|                            | `owner_isikukood` | text   | required; exactly 11 digits — a string, never a number                          |
|                            | `owned_from`      | date   | required                                                                        |
|                            | `owned_until`     | date   | null = current owner; when set, **after `owned_from`**                          |
|                            | `sys_*`           | —      | platform columns, trigger-managed                                               |

Rules: **at most one open period per vessel** (partial unique index on `vessel_id where owned_until is null`). Registering a vessel with an owner (VESSELS.01) also opens its first period, with `owned_from` = registration date. Migration: one open period per existing vessel that has an owner.

## API

| Method + path                          | Does                                                                                                     | Failure answers                                           |
|----------------------------------------|----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `POST /api/vessels/{id}/ownership`     | transfer: body `{ newOwnerIsikukood, transferDate }`; closes the open period, opens a new one, updates `vessel.owner_isikukood`; answers **201 + Location** | 400 validation · 404 · 409 vessel retired · 502 registry down |
| `GET /api/vessels/{id}/ownership`      | ownership history, newest `ownedFrom` first; each row `{ ownerIsikukood, ownedFrom, ownedUntil }`          | 404                                                       |
| `GET /api/vessels/{id}/ownership/{ownershipId}/owner` | name and address of that period's owner via the registry adapter                          | 404 · 502 registry down                                   |
| `PUT /api/vessels/{id}`                | **change to VESSELS.01**: `ownerIsikukood` can only be set when the vessel has no owner yet; otherwise it must equal the current owner | 400 "use the ownership transfer" |

Validation for a transfer (400): code not exactly 11 digits; code unknown to the registry; code equals the current owner; `transferDate` in the future or not after the open period's `owned_from`.

**Failure policy** for a transfer: **fail closed** — if the registry cannot be reached, the transfer answers 502 and nothing is saved. Rationale: a new owner who cannot be verified must not enter the state register. Reading history stays fail open, as in VESSELS.01.

## UI

| Screen                      | Element             | Component                         | Rules                                                                                                       |
|-----------------------------|---------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------|
| Vessel page `/vessels/{id}` | owner code field    | `ResourceForm` field              | read-only once an owner exists                                                                              |
|                             | transfer button     | `AppButtonPrimary`                | hidden for retired vessels; opens the transfer modal                                                        |
|                             | transfer modal      | modal with `Input` + `DatePicker` | new owner code (11 digits) — on blur shows the name and address from the registry; transfer date (default today, not in the future); submit disabled until the registry confirms the code; shows API problem details on failure |
|                             | ownership history   | table                             | columns owner name (live), owner code, from, until ("current" when open); name cell shows "unavailable" when the registry is down |

## Business tests

1. **A transfer closes the old period and opens a new one.** Given a vessel owned by A since 2024-01-01, when it is transferred to B on 2026-11-01, then A's period has `owned_until = 2026-11-01`, B's period is open from 2026-11-01, and the vessel's current owner is B.
2. **The new owner must exist in the population registry.** Given a well-formed code the registry does not know, when a transfer is requested, then it is refused with 400 Bad Request and nothing changes.
3. **No transfer while the registry is down.** Given the registry adapter is unreachable, when a transfer is requested, then it answers 502 Bad Gateway and the ownership history is unchanged.
4. **No transfer to the current owner.** Given a vessel owned by A, when a transfer to A is requested, then it is refused with 400 Bad Request.
5. **A retired vessel cannot change owner.** Given a retired vessel, when a transfer is requested, then it is refused with 409 Conflict.