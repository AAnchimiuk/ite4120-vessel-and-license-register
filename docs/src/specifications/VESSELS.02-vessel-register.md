# VESSELS.02 · License validity and expiries

> **VESSELS.02** · state **Proposed** · traces from [VESSELS-US-002](https://github.com/AAnchimiuk/ite4120-vessel-and-license-register/blob/main/docs/src/user-stories/VESSELS-US-002.md) · builds on [VESSELS.01](VESSELS.01-vessel-register.md) · source `backend/src/main/java/ee/taltech/ite4120/vessels/`, `backend/src/main/resources/vessels/db/changelog/`, `frontend/src/pages/vessels/`, `frontend/src/pages/licenses/`

## Description

Makes the validity of every license explicit and actionable: a derived effective status per license, revocation of a valid license, a full license history per vessel, and a register-wide list of licenses that are about to expire. Validity is **never stored** — it is computed from `issued_on`, `valid_until`, the stored `status` (ACTIVE / REVOKED) and today's date in `Europe/Tallinn`. Excluded: renewal workflow, notifications to owners, editing or deleting licenses.

## Data model

Changes to `vessels.license` (new Liquibase changeset); other tables unchanged.

| Table             | Field               | Type | Rules                                                                                    |
|-------------------|---------------------|------|------------------------------------------------------------------------------------------|
| `vessels.license` | `status`            | text | required; **ACTIVE or REVOKED only** (check constraint); default 'ACTIVE'               |
|                   | `revoked_on`        | date | required when `status = 'REVOKED'`, null otherwise; **not before `issued_on`, not in the future** |
|                   | `revocation_reason` | text | required when `status = 'REVOKED'`, ≤500                                                 |

**Effective status** (computed, returned in API responses as `effectiveStatus`), evaluated top-down:

| Effective status | Condition                                   |
|------------------|---------------------------------------------|
| `REVOKED`        | `status = 'REVOKED'`                        |
| `NOT_YET_VALID`  | `issued_on > today`                         |
| `EXPIRED`        | `valid_until < today`                       |
| `EXPIRING_SOON`  | `valid_until <= today + 30 days`            |
| `VALID`          | otherwise                                   |

`VALID` and `EXPIRING_SOON` both count as *currently valid*.

## API

| Method + path                                            | Does                                                                                                   | Failure answers                    |
|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------------------------|
| `GET /api/vessels/{id}/licenses`                         | **all** licenses of the vessel (history), each with `effectiveStatus`; newest `issuedOn` first          | 404                                |
| `POST /api/vessels/{id}/licenses/{licenseId}/revocation` | revoke: body `{ revokedOn, reason }`; answers **200** with the updated license                          | 400 validation · 404 · 409 not currently valid |
| `GET /api/licenses/expiring`                             | paged list: `withinDays` (default 30, 1–365), `licenseType`, `limit`, `offset`; sorted `validUntil` ascending; active vessels only | 400 bad `withinDays`              |
| `GET /api/licenses/types`                                | reference list of license types                                                                         | —                                  |

Expiring-list rows carry `vesselId`, `vesselName`, `registryCode`, `licenseId`, `licenseNumber`, `licenseType`, `validUntil`, `effectiveStatus`. Errors are `application/problem+json`; mock bearer auth as in VESSELS.01.

## UI

| Screen                      | Element             | Component                         | Rules                                                                                                                       |
|-----------------------------|---------------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Vessel page `/vessels/{id}` | licenses sub-list   | table                             | **all** licenses (replaces "active only" in VESSELS.01); columns number, type, issued, valid until, status tag (colour per effective status) |
|                             | compliance summary  | `AppCard`                         | per license type ever issued: ✓ if at least one currently valid license, ✗ otherwise                                       |
|                             | revoke action       | row action + confirmation modal   | shown only for `VALID` / `EXPIRING_SOON`; fields revocation date (`DatePicker`, default today) and reason (`TextArea`, required) |
| Expiring licenses `/licenses/expiring` | table    | `ResourceList`                    | columns registry code (link to vessel), vessel name, license number, type tag, valid until; sorted soonest first            |
|                             | filters             | `InputNumber` + `Select`          | "within days" (default 30) and license type                                                                                 |
| Navigation                  | menu item           | —                                 | "Expiring licenses" next to "Vessels"                                                                                       |

## Business tests

1. **Effective status follows the dates.** Given today is 2026-11-06, when licenses have `valid_until` 2026-11-05, 2026-11-20 and 2027-06-01 and `issued_on` in the past, then their effective statuses are `EXPIRED`, `EXPIRING_SOON` and `VALID`.
2. **A valid license can be revoked once.** Given a `VALID` license, when it is revoked with today's date and a reason, then it answers 200 with `effectiveStatus = REVOKED`; and when revoked again, then the request is refused with 409 Conflict.
3. **An expired license cannot be revoked.** Given an `EXPIRED` license, when revocation is requested, then it is refused with 409 Conflict.
4. **Revocation needs a reason.** Given a `VALID` license, when revocation is requested with an empty reason, then it is refused with 400 Bad Request ("reason must not be blank").
5. **The expiring list excludes retired vessels.** Given a license expiring in 10 days on a retired vessel, when `GET /api/licenses/expiring?withinDays=30` is called, then that license is not returned.