# VESSELS-US-002 · Track license validity and expiries

As a maritime administration officer (Transpordiamet / Veeteede Amet) I want each license to show whether it is currently valid, and to see which licenses are about to expire, so that I can stop vessels operating without a valid license and remind owners to renew in time — without checking dates by hand across the whole register.

## Context

A license is only useful if its status is clear at a glance. Validity is not stored as a flag: it is derived from the license's issue and expiration dates and today's date, so it can never go stale. The one exception is revocation — the administration can withdraw a license before it expires (e.g. after a failed safety inspection). A revoked license stays in the vessel's history for audit, but is never valid again. One source of truth per fact.

## Acceptance criteria

- Each license shows one of these statuses: *Not yet valid* (issue date in the future), *Valid*, *Expiring soon* (expires within 30 days), *Expired*, or *Revoked*.
- A vessel's detail view shows whether it currently holds at least one valid license of each type it has ever been issued.
- An officer can revoke a valid license with a revocation date and a reason; revoking an already expired or revoked license is refused with a validation error (400).
- Revoked and expired licenses are never deleted; they remain visible in the vessel's license history.
- An "Expiring licenses" list shows all licenses expiring within a chosen number of days (default 30), with vessel name, registry code, license number, type and expiration date, sorted soonest first.
- The expiring list is filterable by license type.
- Licenses of retired (soft-deleted) vessels do not appear in the expiring list.