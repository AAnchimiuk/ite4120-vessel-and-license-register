# Work diary · <First Name Last Name>

> Student: **Olga Maiorova** (`oolga.maiorova@gmail.com`, `olga.maiorova@taltech.ee`)  
> Repository: `ite4120-vessel-and-license-register`  
> Component: **Vessel and License Register** (`vessels`)

This diary tracks development hours, interactions with the AI assistant, mistakes/deviations encountered, and how they were corrected, as required by the course workflow (Section 6 of [Development workflow](../development-workflow.md)).

---

## Log Entries

### Entry 1: User Stories for Licenses (VESSELS-US-002)

- **Date**: 2026-10-16
- **Duration**: 1.0 hours
- **Target Milestone**: Session 2 — Requirements analysis, follow-up user stories for the vessel component.

#### 1. What was done

1. **User story method**:
    - Studied the BABOK v3 user-story technique (10.49): title, statement of value (who / what / why), conversation, acceptance criteria, and the INVEST quality checklist.
    - Created a reusable user-story template with a blank form, a worked example and an INVEST checklist.
2. **Drafting vessel user stories**:
    - Drafted first user stories for vessel registration, permit issuance and inspection lookup, using the component's data (registry code, ship name, vessel type, home port, build year, owner `isikukood`) and the X-Road owner lookup.
    - Compared the drafts with the course example [ANIMALS-US-001](../user-stories/ANIMALS-US-001.md) and rewrote them in the same style: persona with the pain point, a *Context* paragraph explaining "one source of truth per fact", and plain declarative acceptance criteria without technical details.
3. **VESSELS-US-002**:
    - Reviewed the existing [VESSELS-US-001](../user-stories/VESSELS-US-001.md) to avoid overlap — it already covers registration, search, owner lookup and adding licenses.
    - Created [VESSELS-US-002](../user-stories/VESSELS-US-002.md) *Track license validity and expiries*: derived license status (Not yet valid / Valid / Expiring soon / Expired / Revoked), revocation with a reason, license history, and an "expiring licenses" list.
    - Added the file to `docs/src/user-stories/` in VS Code.

#### 2. What I asked the AI agent

- "Find me BABOK user-story template"
- "Turn this into reusable doc"
- "Help with creating 3 user stories for a Vessel & License Register"
- "Compare them with example of Animals Register"

#### 3. What the agent got wrong / Pitfalls encountered

- **Wrong level of detail**: The first drafts mixed business stories with technical design (REST endpoints, table columns, `@helex/ui` components) and used Given/When/Then scenarios, unlike the course's user-story style.
- **Scope overlap**: The first VESSELS-US-002 draft (issuing permits) duplicated what VESSELS-US-001 already covered, because the agent had not yet seen the existing story.
- **Assumptions not from the brief**: The agent assumed the owner was mandatory (US-001 makes it optional) and introduced rules such as "no overlapping permits" and a 30-day expiry window on its own.

#### 4. How it was corrected

- Rewrote the stories against ANIMALS-US-001 so the format matches the course: business language only, technical details moved to the specification.
- Shared VESSELS-US-001 with the agent and re-scoped US-002 to license validity — something US-001 promises ("active and valid") but does not define.
- Kept the agent's extra rules only where agreed and listed the rest as assumptions to confirm with the team.

---

### Entry 2: User Stories US-003/004 and Specifications VESSELS.02–04

- **Date**: 2026-11-06
- **Duration**: 1.0 hours
- **Target Milestone**: Session 3 — Complete the component backlog and specify it before implementation (Process Rule 1).

#### 1. What was done

1. **User stories**:
    - Created [VESSELS-US-003](../user-stories/VESSELS-US-003.md) *Transfer vessel ownership* — the new owner's `isikukood` is verified live via X-Road; ownership is kept as dated periods for history.
    - Created [VESSELS-US-004](../user-stories/VESSELS-US-004.md) *Retire a vessel* — soft delete with date and reason, valid licenses revoked, records kept for audit, registry code freed for reuse.
2. **Specifications** (following the structure of [VESSELS.01](../specifications/VESSELS.01-vessel-register.md)):
    - [VESSELS.02](../specifications/VESSELS.02-license-validity.md): `revoked_on` / `revocation_reason` columns, computed `effectiveStatus`, revocation endpoint, `GET /api/licenses/expiring`, 5 business tests.
    - [VESSELS.03](../specifications/VESSELS.03-ownership-transfer.md): new table `vessels.vessel_ownership`, transfer and history endpoints, **fail-closed** policy when the registry is down, 5 business tests.
    - [VESSELS.04](../specifications/VESSELS.04-vessel-retirement.md): reference table `vessels.retirement_reason`, `POST /api/vessels/{id}/retirement`, retired-vessel filter and read-only view, 5 business tests.

#### 2. What I asked the AI agent

- "Create US-003 and US-004"
- "Create specifications now for US-002, US-003 and US-004" (with a link to VESSELS.01)
- "Create a diary for myself" (with a link to a teammate's diary)

#### 3. What the agent got wrong / Pitfalls encountered

- **Conflicts with VESSELS.01**: The new specifications change parts of the existing one — `DELETE /api/vessels/{id}` is replaced by a retirement endpoint, `GET` must return retired vessels, the license `status` column is limited to ACTIVE / REVOKED, the vessel page shows all licenses instead of active only, and the owner code becomes read-only after the first owner.
- **Inconsistent error codes**: The user stories say refusals are 400, while the specifications answer 409 Conflict when acting on a retired or already-revoked record.
- **Different failure policies**: VESSELS.01 fails open on the owner lookup; VESSELS.03 fails closed on transfer. This is intentional but must be agreed with the team.
- **No direct repo access**: The agent could not write into the local repository, so the files were generated and copied into `docs/src/` manually.

#### 4. How it was corrected

- Every change to VESSELS.01 is marked "**change to VESSELS.01**" in the new specifications so reviewers can see it; VESSELS.01 still needs to be updated to match.
- Documented the fail-closed rationale in VESSELS.03 (an unverified owner must not enter the state register).
- Open items: align error codes between stories and specs, update [SUMMARY.md](../SUMMARY.md) with the new documents, and get team review of all three specifications.

---

### Entry 3: Local environment setup and API testing (vessels)

- **Date**: 2026-11-21
- **Duration**: 3 hours
- **Target Milestone**: Session 4 — Run the application locally and verify the vessels API against the specification.

#### 1. What was done

1. **Toolchain installation (Windows)**:
    - Ran `scripts\setup-tools.ps1` in PowerShell as Administrator; Chocolatey installed Git, GitHub CLI, Temurin JDK 25, Node.js LTS and Python.
    - Docker Desktop failed through Chocolatey (exit code 1603); installed it with the official AMD64 installer instead (WSL 2 backend).
2. **Repository location**:
    - Moved the clone out of OneDrive to local file to avoid sync and file-lock problems with npm and Gradle.
3. **GitHub Packages token**:
    - Created a classic token with `read:packages` only; replaced it with a new one after part of it was accidentally shared.
    - Configured it for Gradle 
4. **Running the backend**:
    - Started PostgreSQL and the Spring Boot backend with `scripts\run-backend.ps1`; confirmed `Started Application in 8.657 seconds`.
    - Opened Swagger UI at `http://localhost:18440/swagger-ui.html` and selected the **Vessel and License Register (vessels)** definition.
    - Confirmed the frontend so far contains only the Animals template pages; the vessels UI is still to be built.
5. **API testing — section 1 (Vessels, VESSELS-US-001)**:
    - Ran the vessel tests in Swagger: list, search by name, filter by type, list retired vessels, single vessel, live owner lookup via the mock registry, registration (201), duplicate registry code (409), build year in the future (400) and malformed personal code (400).
    - Results: <all as expected / list any deviations>.

#### 2. What I asked the AI agent

- Step-by-step explanations to run the application
- Help with moving the repository and renaming the folder
- Help with the GitHub token, including deleting and regenerating it
- Asked for the instructions on how to test the API-s

#### 3. What the agent got wrong / Pitfalls encountered

- **Docker install failed** (Chocolatey exit code 1603) — pending reboot and WSL 2 setup.
- **PowerShell execution policy** blocked `npm.ps1` ("running scripts is disabled on this system").
- **Token check command**: the agent suggested `npm config get` to verify the token, but npm refuses to print protected tokens.
- **401 Unauthorized from Gradle**: a trailing space in `gradle.properties` made the token invalid.
- **Missing frontend**: the agent first assumed a frontend would be started, but the repository has only the Animals example pages; the README describes vessels UI that does not exist yet.
- **Swagger definitions**: the vessels endpoints did not appear because Swagger opened on the Animals definition; the agent first said the sections were only collapsed.
- **Spec vs code mismatch**: the specification says `POST /api/vessels/{id}/retirement`, but the code retires a vessel with `DELETE /api/vessels/{id}` and query parameters.

#### 4. How it was corrected

- Rebooted, installed Docker Desktop with the official installer and confirmed it runs.
- Ran `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.
- Verified the npm token with `npm config list --global` (shows `(protected)`) instead.
- Retyped `gradle.properties` without spaces and stopped old Gradle daemons with `gradlew.bat --stop`.
- Switched Swagger to the **Vessel and License Register (vessels)** definition.
- Open items: align the retirement endpoint between spec and code; continue API testing with sections 2–4 (licenses, ownership, retirement); build the vessels frontend pages.

---

### Entry 4: Vessels frontend

- **Date**: 2026-11-23
- **Duration**: <X> hours
- **Target Milestone**: Session 4 — UI for the vessels component (VESSELS.01 § UI).

#### 1. What was done

1. **Frontend pages** (React + `@helex/ui`, following the Animals example):
    - `pages/vessels/VesselList.tsx` — `ResourceList` with search by name or registry code, filters for vessel type and Active / Retired, owner details in the side panel (fail open).
    - `pages/vessels/VesselCreate.tsx` — registration form.
    - `pages/vessels/VesselDetail.tsx` — `ResourceForm` view/edit, retired banner, metadata sidebar, Retire vessel action.
    - `pages/vessels/LicensesCard.tsx` — license history with effective status, compliance summary per type, add / revoke / delete (scheduled only).
    - `pages/vessels/OwnershipCard.tsx` — ownership history with live owner names, transfer dialog (fail closed).
    - `pages/vessels/RetireModal.tsx` — date, reason, note (required for OTHER), warning with the number of valid licenses to be revoked.
    - `pages/licenses/ExpiringLicenses.tsx` — licenses expiring within N days, filter by type.
    - `vesselsApi.ts` (API client), routes in `App.tsx`, app name changed to "Vessel and License Register".
2. **Verification**: `npx tsc -b` passed; tested in the browser — search, owner panel, add license (incl. "valid until before issue date" refused), revoke, transfer (incl. unknown person refused), retirement, retired filter, reuse of registry code EST-5005.
3. **Spec**: VESSELS.01 § UI updated where the implementation differs.
4. **Pull request** #2 `feature/vessels-frontend` → `main`, review requested from AAnchimiuk.

#### 2. What I asked the AI agent

- Let's create a frotend now
- Step-by-step help with branch, `npm install`, typecheck, running the frontend, testing, commit and pull request

#### 3. What the agent got wrong / Pitfalls encountered

- **Retire button not visible**: `ResourceForm` puts the delete action in the "…" menu, so it was hard to find.
- **Edit button on retired vessels**: first shown an error on click instead of being hidden; the first fix did not reach the local files and had to be saved again.
- **Spec differences**: register form uses `AppCard` + `Form` instead of `ResourceForm`; transfer dialog has no live owner preview (the API has no lookup for an arbitrary personal code); navigation uses buttons instead of a top menu.
- **Checks not running**: GitHub Actions are disabled on this fork, so the Verify checks did not run on the pull request.

#### 4. How it was corrected

- Added a visible "Retire vessel" button in the sidebar; Edit and Retire are hidden for retired vessels (`onEdit` / `onDelete` left out).
- Re-saved the file and confirmed with a second typecheck.
- Updated VESSELS.01 § UI to match the implementation (Process Rule 3).
- Open items: team decision on enabling Actions; review and merge of PR #2.

---

### Time Summary

| Milestone / Task                                                              | Date       | Hours       |
|:------------------------------------------------------------------------------|:-----------|:------------|
| Session 1: BABOK template, user story drafts, comparison, VESSELS-US-002       | 2026-10-16 | 1 h       |
| Session 2: VESSELS-US-003/004, specifications VESSELS.02–04, diary            | 2026-11-06 | 1 h       |
| Session 3: Local setup (tools, Docker, token), backend run, vessels API tests  | 2026-11-21 | 3 h       |
| Session 4: Vessels frontend, browser testing, pull request #2                  | 2026-11-23 | 3 h         |
| **Total**                                                                     |            | **8 h**   |