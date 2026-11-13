# VESSELS.01 · Vessel and license register

> **VESSELS.01** · state **Proposed** · traces from [VESSELS-US-001](../user-stories/VESSELS-US-001.md), [VESSELS-US-002](../user-stories/VESSELS-US-002.md), [VESSELS-US-003](../user-stories/VESSELS-US-003.md), [VESSELS-US-004](../user-stories/VESSELS-US-004.md)  
> · source `backend/src/main/java/ee/taltech/ite4120/vessels/`, `backend/src/main/resources/vessels/db/changelog/`, `frontend/src/pages/vessels/`, `frontend/src/pages/licenses/`

## Description

A unified register of maritime vessels, their operational licenses, ownership history, and retirement records:
- **Vessel Lifecycle**: Register, search, inspect, update, and retire maritime watercraft and commercial ships. Registry codes are unique among active vessels; once a vessel is retired, its registry code is freed for reuse.
- **License Lifecycle & Validity**: Issue operational maritime licenses with validity periods (`issued_on`, `valid_until`). License validity is derived dynamically—never stored as a mutable flag—and evaluated against the current date and stored status (`ACTIVE` vs `REVOKED`). An officer can revoke a valid license with a mandatory date and reason. A register-wide expiring licenses view provides monitoring for licenses nearing expiration (default within 30 days).
- **Ownership Lifecycle & Audit History**: Owner identity is never duplicated as free text; it is stored solely as the owner's personal identification code (`isikukood`). Ownership is recorded in dated periods (`vessels.vessel_ownership`), maintaining complete historical auditability. When transferring ownership, the new personal code is validated live against the Estonian population registry over X-Road before committing.
- **Recorded Retirement**: An officer can retire a vessel with a date, reason (from a reference list), and explanatory note. In a single atomic transaction, retirement soft-deletes the vessel row (`sys_status = 'D'`), frees the registry code for reuse, automatically revokes all currently valid licenses as of the retirement date, and freezes the vessel against further modifications, new licenses, or ownership transfers while keeping all historical data readable.
- **External Registry Integration**: Live population registry lookup for owner full name and address. Queries follow a fail-open policy (registry unavailability returns 502 on the owner endpoint only, leaving the vessel record fully operable), while ownership transfer follows a fail-closed policy (registry unavailability rejects the transfer to prevent unverified owners from entering the state register).

**Excluded**: Authentication management (mock bearer auth only), multi-tenancy, co-ownership/shares, company registry codes (`registrikood`), physical hard deletion, and external archiving systems.

## Data model

The `vessels` schema consists of two reference tables and three business tables:

| Table | Field | Type | Rules |
| --- | --- | --- | --- |
| `vessels.vessel_type` | `code` PK | text | reference list: FISHING, CARGO, PASSENGER, PLEASURE, SAILING, TUG |
| | `name` | text | display name |
| `vessels.retirement_reason` | `code` PK | text | reference list: SCRAPPED, LOST, REFLAGGED, OTHER |
| | `name` | text | display name |
| `vessels.vessel` | `id` PK | bigint | from `core.seq_id` |
| | `registry_code` | text | required; **unique among active rows** (`where sys_status = 'A'`) |
| | `name` | text | required, ≤255 |
| | `type_code` | text | required; FK → `vessels.vessel_type` |
| | `build_year` | integer | optional; **not in the future** (≤ current year) |
| | `home_port` | text | optional, ≤100 |
| | `gross_tonnage` | numeric | optional, > 0 |
| | `owner_isikukood` | text | optional; current owner; exactly 11 digits — string, never a number; read-only once set (subsequent changes use ownership transfer) |
| | `retired_on` | date | null while active; set on retirement; **not in the future** |
| | `retirement_reason` | text | required when `retired_on` is set; FK → `vessels.retirement_reason` |
| | `retirement_note` | text | ≤500; **required when reason = 'OTHER'** |
| | `sys_*` | — | platform columns, trigger-managed |
| `vessels.license` | `id` PK | bigint | from `core.seq_id` |
| | `vessel_id` | bigint | required; FK → `vessels.vessel` |
| | `license_number` | text | required, ≤50 |
| | `license_type` | text | required (e.g. NAVIGATION, FISHING, PASSENGER) |
| | `issued_on` | date | required |
| | `valid_until` | date | required; **must be on or after `issued_on`** |
| | `status` | text | required; check constraint: **`ACTIVE` or `REVOKED` only**; default `'ACTIVE'` |
| | `revoked_on` | date | required when `status = 'REVOKED'`, null otherwise; **not before `issued_on`, not in the future** |
| | `revocation_reason` | text | required when `status = 'REVOKED'`, ≤500 |
| | `sys_*` | — | platform columns, trigger-managed |
| `vessels.vessel_ownership` | `id` PK | bigint | from `core.seq_id` |
| | `vessel_id` | bigint | required; FK → `vessels.vessel` |
| | `owner_isikukood` | text | required; exactly 11 digits — string, never a number |
| | `owned_from` | date | required |
| | `owned_until` | date | null = current owner; when set, **on or after `owned_from`** |
| | `sys_*` | — | platform columns, trigger-managed |

### Ownership & Integrity Constraints

- **At most one open ownership period per vessel**: Enforced by partial unique index `create unique index idx_vessel_ownership_current on vessels.vessel_ownership (vessel_id) where (owned_until is null)`.
- **Initial ownership**: Registering a vessel with an owner opens its first ownership period with `owned_from = current_date`.
- **Code reuse on retirement**: On retirement, the vessel row has `sys_status = 'D'`. Because the unique index on `registry_code` is partial (`where sys_status = 'A'`), the registry code is immediately freed for future active vessels.

### Derived Effective Status for Licenses

License validity is computed dynamically in `Europe/Tallinn` and returned as `effectiveStatus` in API responses, evaluated top-down:

| Effective status | Condition | Operational meaning |
| --- | --- | --- |
| `REVOKED` | `status = 'REVOKED'` | Withdrawn by administration; invalid permanently |
| `NOT_YET_VALID` | `issued_on > today` | Scheduled for future validity |
| `EXPIRED` | `valid_until < today` | Validity term lapsed; invalid |
| `EXPIRING_SOON` | `valid_until <= today + 30 days` | Currently valid, but requires renewal within 30 days |
| `VALID` | otherwise | Currently valid |

Both `VALID` and `EXPIRING_SOON` count as **currently valid**.

## API

| Method + path | Does | Failure answers |
| --- | --- | --- |
| `GET /api/vessels` | paged search: `textContains` (matches code or name), `typeCode`, `retired` (default `false`), `limit`, `offset`, `sort` | — |
| `GET /api/vessels/{id}` | returns vessel details (active or retired; includes retirement fields when retired) | 404 |
| `POST /api/vessels` | register vessel; if `ownerIsikukood` is present, opens initial ownership period; answers **201 + Location** | 400 validation · 409 duplicate code on active vessel |
| `PUT /api/vessels/{id}` | update details (name, buildYear, homePort, grossTonnage); `registryCode` immutable; `ownerIsikukood` can only be set initially if vessel had no owner | 400 validation · 404 · 409 vessel retired or invalid owner modification |
| `POST /api/vessels/{id}/retirement` | retire vessel: body `{ retiredOn, reason, note? }`; atomically sets retirement fields, soft-deletes row, and revokes all currently valid licenses (`revokedOn = retiredOn`, reason "Vessel retired"); answers **200** | 400 validation · 404 · 409 already retired |
| `GET /api/vessels/{id}/owner` | current owner full name and address via external registry adapter (fail open) | 404 (no owner or not found) · 502 registry down |
| `POST /api/vessels/{id}/ownership` | transfer ownership: body `{ newOwnerIsikukood, transferDate }`; validates new code live with registry (fail closed); closes active period, opens new period, updates `vessel.owner_isikukood`; answers **201 + Location** | 400 validation · 404 · 409 vessel retired · 502 registry down |
| `GET /api/vessels/{id}/ownership` | list ownership history periods, newest `ownedFrom` first | 404 |
| `GET /api/vessels/{id}/ownership/{ownershipId}/owner` | owner full name and address for a specific historical ownership period (fail open) | 404 · 502 registry down |
| `GET /api/vessels/{id}/licenses` | all licenses of the vessel (history), each with `effectiveStatus`; newest `issuedOn` first | 404 |
| `POST /api/vessels/{id}/licenses` | issue new license: body `{ licenseNumber, licenseType, issuedOn, validUntil }`; answers **201 + Location** | 400 validation (validUntil < issuedOn) · 404 · 409 vessel retired |
| `POST /api/vessels/{id}/licenses/{licenseId}/revocation` | revoke a currently valid license: body `{ revokedOn, reason }`; answers **200** with updated license | 400 validation · 404 · 409 not currently valid or vessel retired |
| `GET /api/licenses/expiring` | paged list: `withinDays` (default 30, 1–365), `licenseType`, `limit`, `offset`; sorted `validUntil` ascending; active vessels only | 400 bad `withinDays` |
| `GET /api/vessels/types` | reference list of vessel types | — |
| `GET /api/vessels/retirement-reasons` | reference list of retirement reasons | — |
| `GET /api/licenses/types` | reference list of license types | — |

Every error response is formatted as `application/problem+json`. All endpoints require `Authorization: Bearer <user>` (mock bearer auth); the API contract is exposed under `/swagger-ui.html`.

### External Registry Failure Policies

- **Fail open for read lookups**: `GET /api/vessels/{id}/owner` and `GET /api/vessels/{id}/ownership/{ownershipId}/owner` return 502 Bad Gateway if the external population registry is unreachable, but the vessel record, licenses, and ownership periods remain fully queryable. The UI gracefully displays "Unavailable" for owner details.
- **Fail closed for ownership transfer**: `POST /api/vessels/{id}/ownership` returns 502 Bad Gateway if the external registry is unreachable, refusing the transfer and committing nothing. An unverified person must not be recorded as the owner in the state register.

## UI

| Screen | Element | Component | Rules |
| --- | --- | --- | --- |
| Vessel list | table | `ResourceList` | columns: registry code (locked, **a link to the vessel page**), name, type tag, home port, build year, status tag ("Active" / "Retired" with retirement date); search input (code or name); filter dropdowns |
| | filter | `Select` / toggle | "Active" (default) / "Retired" |
| | register button | `AppButtonPrimary` | navigates to registration form |
| | detail panel | `ResourceList.detailView` | displays owner name and address fetched via `/owner`; gracefully indicates unavailable when registry is down |
| Vessel page `/vessels/{id}` | view / edit | `ResourceForm` + `useDataController` | sections Identity (registry code read-only, name, type) and Details (build year, home port, gross tonnage, owner code); Edit → Save is `PUT`, Cancel restores; all edit actions hidden/disabled for retired vessels |
| | retired banner | `AppCard` / alert | displayed prominently on retired vessels: shows retirement date, reason, and note |
| | owner section | `ResourceForm` field | personal code plus live name and address via `/owner`; once set, personal code is read-only |
| | transfer action | `AppButtonPrimary` | hidden for retired vessels; opens Transfer Ownership modal |
| | transfer modal | modal with `Input` + `DatePicker` | new owner code (11 digits, on blur fetches name and address from registry); transfer date (`DatePicker`, default today, not in future); submit disabled until registry validates code; displays API problem details on error |
| | ownership history | table | columns: owner name (live from registry), personal code, from date, until date ("current" for active period); shows "Unavailable" when registry is down |
| | licenses sub-list | table | **all** licenses; columns: license number, type, issued date, valid until date, status tag (color per `effectiveStatus`); row action "Revoke" |
| | compliance summary | `AppCard` | per license type ever issued: ✓ if at least one currently valid license (`VALID` or `EXPIRING_SOON`), ✗ otherwise |
| | revoke action | row action + modal | visible only for `VALID` or `EXPIRING_SOON` licenses; fields: revocation date (`DatePicker`, default today, not in future), reason (`TextArea`, required); confirmation dialog |
| | add license action | `AppButtonPrimary` | hidden for retired vessels; opens modal with license number, type, issued date, valid until date |
| | retire action | `AppButtonPrimary` (danger) | hidden for retired vessels; opens Retire Vessel modal |
| | retire modal | modal with `DatePicker`, `Select`, `TextArea` | retirement date (default today, not in future); reason (`Select` from `/retirement-reasons`); note (`TextArea`, required when reason is OTHER); shows warning with count of valid licenses that will be revoked |
| | metadata | `ResourceForm.sidebar` — `AppCard` of `FieldItem`s | created at / by, modified at / by, version — platform `sys_*` columns |
| Register form | form | `ResourceForm` | registry code (`Input`, required, ≤50), name (`Input`, required, ≤255), type (`Select`, required), build year (`InputNumber`, optional, min 1800, max current year), home port (`Input`, optional, ≤100), gross tonnage (`InputNumber`, optional, positive), owner code (`Input`, optional, 11 digits); submit shows API problem details on failure |
| Expiring licenses `/licenses/expiring` | table | `ResourceList` | columns: registry code (link to vessel page), vessel name, license number, type tag, valid until, status tag (`EXPIRING_SOON`); sorted soonest first |
| | filters | `InputNumber` + `Select` | "within days" (default 30) and license type |
| Navigation | menu item | — | "Expiring licenses" next to "Vessels" in top-level navigation |

## Business tests

1. **A vessel cannot have a build year in the future.**
   Given a vessel registration request with a build year greater than the current year,
   when submitted, then the registration is refused with 400 Bad Request ("buildYear must not be in the future").

2. **A registry code is unique among active vessels and reusable after retirement.**
   Given an active vessel with registry code X,
   when another vessel registration claims registry code X,
   then the registration is refused with 409 Conflict.
   And when the initial vessel is retired (soft-deleted),
   registry code X may be registered again for a new vessel.

3. **A license expiration date cannot precede its issue date.**
   Given a license issuance request where `validUntil` is earlier than `issuedOn`,
   when submitted, then the request is refused with 400 Bad Request ("validUntil must not precede issuedOn").

4. **License effective status follows the dates dynamically.**
   Given today is 2026-11-06,
   when licenses have `issued_on` in the past and `valid_until` dates 2026-11-05, 2026-11-20, and 2027-06-01,
   then their derived `effectiveStatus` values are `EXPIRED`, `EXPIRING_SOON`, and `VALID` respectively.

5. **A valid license can be revoked once.**
   Given a license with effective status `VALID` or `EXPIRING_SOON`,
   when revoked with today's date and a reason,
   then it answers 200 OK with `effectiveStatus = REVOKED`;
   and when revocation is requested again,
   then the request is refused with 409 Conflict ("license is already revoked").

6. **An expired license cannot be revoked.**
   Given an `EXPIRED` license,
   when revocation is requested,
   then the request is refused with 409 Conflict ("license is not currently valid").

7. **License revocation requires a non-blank reason.**
   Given a `VALID` license,
   when revocation is requested with an empty or blank reason,
   then the request is refused with 400 Bad Request ("reason must not be blank").

8. **Expiring licenses list excludes retired vessels.**
   Given a license expiring within 10 days on a retired vessel and another on an active vessel,
   when `GET /api/licenses/expiring?withinDays=30` is called,
   then only the license belonging to the active vessel is returned.

9. **Ownership transfer closes the old period and opens a new one.**
   Given a vessel owned by owner A since 2024-01-01,
   when transferred to owner B on 2026-11-01,
   then owner A's period has `owned_until = 2026-11-01`, owner B's period has `owned_from = 2026-11-01` with `owned_until = null`, and `vessel.owner_isikukood` is updated to B.

10. **New owner must exist in the population registry (fail closed).**
    Given a well-formed 11-digit personal code unknown to the population registry,
    when ownership transfer is requested,
    then the request is refused with 400 Bad Request ("owner not found in population registry") and no changes are committed.

11. **No ownership transfer while the population registry is unreachable.**
    Given the external registry adapter is down,
    when an ownership transfer is requested,
    then the request fails with 502 Bad Gateway and the ownership history remains unchanged.

12. **Cannot transfer ownership to the current owner.**
    Given a vessel currently owned by owner A,
    when ownership transfer to owner A is requested,
    then the request is refused with 400 Bad Request ("new owner must be different from current owner").

13. **Vessel retirement automatically revokes all currently valid licenses.**
    Given an active vessel with one `VALID` license and one `EXPIRED` license,
    when the vessel is retired on 2026-11-06 with reason `SCRAPPED`,
    then the `VALID` license becomes `REVOKED` with `revoked_on = 2026-11-06` and reason "Vessel retired", while the `EXPIRED` license remains unchanged.

14. **A vessel cannot be retired twice.**
    Given an already retired vessel,
    when retirement is requested again,
    then the request is refused with 409 Conflict ("vessel is already retired").

15. **Retirement with reason OTHER requires a non-blank note.**
    Given an active vessel,
    when retired with reason `OTHER` and an empty note,
    then the request is refused with 400 Bad Request ("retirementNote is required when reason is OTHER").

16. **A retired vessel remains readable but frozen against mutations.**
    Given a retired vessel,
    when `GET /api/vessels/{id}` is called, then it returns 200 OK with the retirement details;
    and when a license issuance, ownership transfer, or update (`PUT`) is submitted for that vessel,
    then the request is refused with 409 Conflict ("vessel is retired").
