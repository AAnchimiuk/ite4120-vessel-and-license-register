# VESSELS.04 · Vessel retirement

> **VESSELS.04** · state **Proposed** · traces from [VESSELS-US-004](https://github.com/AAnchimiuk/ite4120-vessel-and-license-register/blob/main/docs/src/user-stories/VESSELS-US-004.md) · builds on [VESSELS.01](VESSELS.01-vessel-register.md), [VESSELS.02](VESSELS.02-license-validity.md), [VESSELS.03](VESSELS.03-ownership-transfer.md) · source `backend/src/main/java/ee/taltech/ite4120/vessels/`, `backend/src/main/resources/vessels/db/changelog/`, `frontend/src/pages/vessels/`

## Description

Turns the bare soft delete of VESSELS.01 into a recorded retirement: a vessel leaves the active register with a date and a reason, every license still valid on that date is revoked, and the vessel's data, ownership and license history stay readable. The registry code becomes free for reuse, as VESSELS.01 already allows. Excluded: restoring a retired vessel, physical deletion, archiving to another system.

## Data model

New reference table and columns on `vessels.vessel` (new Liquibase changeset).

| Table                           | Field               | Type | Rules                                                                     |
|---------------------------------|---------------------|------|---------------------------------------------------------------------------|
| `vessels.retirement_reason`     | `code` PK           | text | reference list: SCRAPPED, LOST, REFLAGGED, OTHER                         |
|                                 | `name`              | text | display name                                                              |
| `vessels.vessel`                | `retired_on`        | date | null while active; set on retirement; **not in the future**              |
|                                 | `retirement_reason` | text | required when `retired_on` is set; FK → `vessels.retirement_reason`      |
|                                 | `retirement_note`   | text | ≤500; **required when reason = OTHER**                                    |

On retirement the row leaves `sys_status = 'A'` (soft delete, so the unique index on `registry_code` from VESSELS.01 frees the code). Retired rows must still be readable through the endpoints below.

## API

| Method + path                       | Does                                                                                                         | Failure answers                    |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------|
| `POST /api/vessels/{id}/retirement` | retire: body `{ retiredOn, reason, note? }`; in one transaction sets the retirement fields, soft-deletes the row and revokes every currently valid license with `revokedOn = retiredOn`, reason "Vessel retired"; answers **200** with the retired vessel | 400 validation · 404 · 409 already retired |
| `DELETE /api/vessels/{id}`          | **change to VESSELS.01**: removed — retirement always goes through `/retirement`                              | —                                  |
| `GET /api/vessels`                  | **change to VESSELS.01**: new parameter `retired` (`false` default, `true` = only retired vessels)           | —                                  |
| `GET /api/vessels/{id}`             | **change to VESSELS.01**: also returns retired vessels, with `retiredOn`, `retirementReason`, `retirementNote` | 404                                |
| `GET /api/vessels/retirement-reasons` | the reference list                                                                                         | —                                  |
| `POST /api/vessels/{id}/licenses`, `POST /api/vessels/{id}/ownership`, `PUT /api/vessels/{id}` | refused for a retired vessel                                                          | 409 vessel retired                 |

Errors are `application/problem+json`; mock bearer auth as in VESSELS.01.

## UI

| Screen                      | Element           | Component                     | Rules                                                                                                       |
|-----------------------------|-------------------|-------------------------------|-------------------------------------------------------------------------------------------------------------|
| Vessel list                 | filter            | `Select` or toggle            | "Active" (default) / "Retired"; retired rows show a "Retired" tag and the retirement date                  |
| Vessel page `/vessels/{id}` | retire button     | `AppButtonPrimary` (danger)   | replaces the soft-delete Retire of VESSELS.01; hidden for retired vessels; opens the retire modal          |
|                             | retire modal      | modal with `DatePicker`, `Select`, `TextArea` | retirement date (default today, not in the future); reason from `/retirement-reasons`; note required when OTHER; warns how many valid licenses will be revoked |
|                             | retired banner    | `AppCard` / alert             | shown on a retired vessel: date, reason, note; Edit, Transfer and Add license actions hidden               |
|                             | histories         | as VESSELS.02 / .03           | license and ownership history stay visible                                                                  |

## Business tests

1. **Retirement revokes valid licenses.** Given an active vessel with one `VALID` and one `EXPIRED` license, when it is retired on 2026-11-06, then the valid license becomes `REVOKED` with `revokedOn = 2026-11-06` and reason "Vessel retired", and the expired license is unchanged.
2. **A vessel cannot be retired twice.** Given a retired vessel, when retirement is requested again, then it is refused with 409 Conflict.
3. **OTHER needs a note.** Given an active vessel, when it is retired with reason OTHER and no note, then the request is refused with 400 Bad Request.
4. **A retired vessel stays readable but frozen.** Given a retired vessel, when `GET /api/vessels/{id}` is called, then it answers 200 with the retirement details; and when a license or ownership transfer is posted for it, then the request is refused with 409 Conflict.
5. **The registry code is freed.** Given a vessel with registry code X retired, when a new vessel is registered with registry code X, then it answers 201 Created.