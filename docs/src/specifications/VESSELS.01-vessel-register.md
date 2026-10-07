# VESSELS.01 · Vessel and license register

> **VESSELS.01** · state **Proposed** · traces from [VESSELS-US-001](../user-stories/VESSELS-US-001.md)
> · source `backend/src/main/java/ee/taltech/ite4120/vessels/`, `backend/src/main/resources/vessels/db/changelog/`, `frontend/src/pages/vessels/`

## Description

A register of maritime vessels and their operational licenses: create, search, view, update,
and retire (soft delete) vessels, as well as record and inspect their licenses.
Owner identity is held as a personal identification code (`isikukood`) only; full name and
residential address are resolved live from the population registry through the owner-registry
adapter (mock locally, X-Road-shaped in production).
Excluded: authentication management (mock auth only), multi-tenancy, audit beyond the platform sys columns.

## Data model

| Table | Field | Type | Rules |
| --- | --- | --- | --- |
| `vessels.vessel_type` | `code` PK | text | reference list: FISHING, CARGO, PASSENGER, PLEASURE, SAILING, TUG |
| | `name` | text | display name |
| `vessels.vessel` | `id` PK | bigint | from `core.seq_id` |
| | `registry_code` | text | required; **unique among active rows** (`where sys_status = 'A'`) |
| | `name` | text | required, ≤255 |
| | `type_code` | text | required; FK → `vessels.vessel_type` |
| | `build_year` | integer | optional; **not in the future** |
| | `home_port` | text | optional, ≤100 |
| | `gross_tonnage` | numeric | optional, > 0 |
| | `owner_isikukood` | text | optional; exactly 11 digits — a string, never a number |
| | `sys_*` | — | platform columns, trigger-managed |
| `vessels.license` | `id` PK | bigint | from `core.seq_id` |
| | `vessel_id` | bigint | required; FK → `vessels.vessel` |
| | `license_number` | text | required, ≤50 |
| | `license_type` | text | required (e.g. NAVIGATION, FISHING, PASSENGER) |
| | `issued_on` | date | required |
| | `valid_until` | date | required; **must be on or after `issued_on`** |
| | `status` | text | required; default 'ACTIVE' |
| | `sys_*` | — | platform columns, trigger-managed |

## API

| Method + path | Does | Failure answers |
| --- | --- | --- |
| `GET /api/vessels` | paged search: `textContains`, `typeCode`, `limit`, `offset`, `sort` | — |
| `GET /api/vessels/{id}` | one active vessel | 404 |
| `POST /api/vessels` | register; answers **201 + Location** | 400 validation · 409 duplicate code |
| `PUT /api/vessels/{id}` | update; registry code immutable | 400 · 404 |
| `DELETE /api/vessels/{id}` | retire (soft delete) — **204** | 404 |
| `GET /api/vessels/{id}/owner` | owner via the registry adapter | 404 · 502 registry down |
| `GET /api/vessels/types` | the reference list | — |
| `GET /api/vessels/{id}/licenses` | list licenses for a vessel | 404 |
| `POST /api/vessels/{id}/licenses` | record license; answers **201 + Location** | 400 · 404 |

Every error response is formatted as `application/problem+json`. All endpoints require
`Authorization: Bearer <user>` (mock auth); the API contract is exposed under `/swagger-ui.html`.

**Failure policy** for the owner lookup: **fail open** — an unreachable registry answers 502
on the `/owner` endpoint only; the vessel record remains fully queryable and operable.
Rationale: the owner's personal details are auxiliary lookup data, not a prerequisite for vessel operations.

## UI

| Screen | Element | Component | Rules |
| --- | --- | --- | --- |
| Vessel list | table | `ResourceList` | columns: registry code (locked, **a link to the vessel page**), name, type tag, home port, build year; search input; filter dropdown |
| | register button | `AppButtonPrimary` | navigates to registration form |
| | detail panel | `ResourceList.detailView` | displays owner name and address fetched via `/owner`; omitted when registry is unavailable |
| Vessel page `/vessels/{id}` | view / edit | `ResourceForm` + `useDataController` | sections Identity (registry code read-only, name, type) and Details (build year, home port, gross tonnage, owner code); Edit → Save is `PUT`, Cancel restores; Retire is soft `DELETE` with confirmation |
| | owner | `ResourceForm` field | personal code plus live name and address via `/owner` |
| | licenses sub-list | table / panel | displays active licenses for the vessel |
| | metadata | `ResourceForm.sidebar` — `AppCard` of `FieldItem`s | created at / by, modified at / by, version — platform `sys_*` columns |
| Register form | registry code | `Input` | required, ≤50 |
| | name | `Input` | required, ≤255 |
| | type | `Select` | required, options from `/types` |
| | build year | `InputNumber` | optional, min 1800, max current year |
| | home port | `Input` | optional, ≤100 |
| | gross tonnage | `InputNumber` | optional, positive |
| | owner code | `Input` | optional, 11 digits |
| | submit | `AppButtonPrimary` | shows API problem details in notification on failure |

## Business tests

1. **A vessel cannot have a build year in the future.**
   Given a vessel registration request with a build year greater than the current year,
   when submitted, then the registration is refused with 400 Bad Request ("buildYear must not be in the future").

2. **A registry code is unique among active vessels.**
   Given an active vessel with registry code X,
   when another vessel registration claims registry code X,
   then the registration is refused with 409 Conflict.
   And when the initial vessel is retired (soft deleted),
   registry code X may be registered again.

3. **A license expiration date cannot precede its issue date.**
   Given a license issuance request where `validUntil` is earlier than `issuedOn`,
   when submitted, then the request is refused with 400 Bad Request ("validUntil must not precede issuedOn").

