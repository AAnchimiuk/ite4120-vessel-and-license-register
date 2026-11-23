# Work diary · Aleksander Anchimiuk

> Student: **Aleksander Anchimiuk** (`aleksanderanchimiuk@gmail.com`,`aleanc@taltech.ee`)  
> Repository: `ite4120-vessel-and-license-register`  
> Component: **Vessel and License Register** (`vessels`)

This diary tracks development hours, interactions with the AI assistant, mistakes/deviations encountered, and how they were corrected, as required by the course workflow (Section 6 of [Development workflow](../development-workflow.md)).

---

## Log Entries

### Entry 1: Environment Setup & Component Specification

* **Date**: 2026-10-07
* **Duration**: 2.5 hours
* **Target Milestone**: Session 1 — Toolchain baseline, template verification, component user story & specification.

#### 1. What was done
1. **Toolchain & Runtime Diagnostics**:
   * Evaluated current machine status against course requirements (Git, Docker, JDK 25, Node.js 20+, Python 3).
   * Located Oracle JDK 25 installation at `/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home` and exported `JAVA_HOME` into `~/.zprofile`.
   * Configured `PATH` in `~/.zprofile` to prioritize Homebrew's Node.js 23 (`/usr/local/opt/node/bin`) and npm 10 over system Node 18 to avoid engine compatibility warnings with Vite and React Router 7.
2. **GitHub Packages Credential Wiring**:
   * Identified existing classic GitHub Personal Access Token (`read:packages`) in `/usr/local/etc/npmrc`.
   * Configured Gradle credentials in `~/.gradle/gradle.properties` (`gpr.user=AAnchimiuk` and `gpr.key=<token>`) so Gradle can resolve mirrored artifacts (`org.helex.emr:*`) from the GitHub Maven registry.
   * Configured user-level `~/.npmrc` to allow npm to download `@helex-solutions` packages without 401 Unauthorized errors.
3. **Template Verification**:
   * Executed `./gradlew classes` to prove compilation against downloaded Helex jars.
   * Executed `npm install` and `npm run typecheck` (`tsc -b`) in `frontend/` to confirm zero TypeScript errors.
   * Booted PostgreSQL (`ite4120-postgres-1`) and started backend (`./scripts/run-backend.sh`) and frontend (`./scripts/run-frontend.sh`). Verified `http://localhost:18640` and Swagger at `http://localhost:18440/swagger-ui.html`.
4. **Drafting Component User Story & Specification**:
   * Created [VESSELS-US-001](../user-stories/VESSELS-US-001.md) based on `docs/_templates/user-story.md`.
   * Created [VESSELS.01](../specifications/VESSELS.01-vessel-register.md) based on `docs/_templates/specification.md`.
   * Defined schema `vessels` with 3 tables: `vessels.vessel_type`, `vessels.vessel`, and `vessels.license`.
   * Specified REST API endpoints, fail-open external registry policy, UI screens (`ResourceList`, `ResourceForm`), and 3 testable business rules.
   * Updated [SUMMARY.md](../SUMMARY.md) to integrate the new documents into the mdBook table of contents.

#### 2. What I asked the AI agent
* "Please help me step by step to run this project according to readme"
* "Please stop it now" (after confirming local servers were operating properly)
* "Please support me in designing app with the same structure but for vessel and license register lets go start with creating docs for new component"
* "Also I need to have personal diary explaining what I have done and how much time it take"

#### 3. What the agent got wrong / Pitfalls encountered
* **Java runtime detection**: When executing `java -version` initially, the command failed because `/usr/bin/java` looked for a broken symlink rather than the installed `jdk-25.jdk`. The agent needed to explicitly inspect `/Library/Java/JavaVirtualMachines/` and set `JAVA_HOME`.
* **Gradle loopback socket in sandbox**: The Gradle daemon communicated over a local TCP loopback port (`127.0.0.1`), which was initially blocked by the strict execution sandbox, causing a connection failure. Unsandboxed execution was required to connect to the daemon and reach external package registries.
* **Global npm configuration mismatch**: Node 23 from Homebrew ignored `/usr/local/etc/npmrc` where the token was initially stored, resulting in `npm error 401 Unauthorized` when downloading `@helex-solutions/ui`.
* **Git branch ref lock**: Attempted branch creation failed within the sandboxed filesystem due to permissions on `.git/refs/heads/`.

#### 4. How it was corrected
* Explicitly exported `JAVA_HOME` pointing to `jdk-25.jdk` and added it to `~/.zprofile`.
* Copied npm token to `~/.npmrc` so both system and Homebrew node/npm versions authenticate seamlessly.
* Mirrored token to `~/.gradle/gradle.properties` so the Gradle build resolves all dependencies.
* Documented the new specifications in `docs/` directly to ensure compliance with Process Rule 1 before touching any code.

---

### Entry 2: Team Synchronization & Project Review

* **Date**: 2026-10-22
* **Duration**: 1.0 hour
* **Target Milestone**: Session 1 — Team alignment, component ideation review, next steps.

#### 1. What was done
1. **Group Sync Call**:
   * Held a 1-hour synchronization call with team members to align on project architecture, goals, and working conventions.
   * Discussed technicalities of the platform template: Spring Boot backend, Liquibase migrations, Testcontainers integration testing, and React with `@helex/ui`.
   * Outlined development workflow and planned upcoming milestones for the component implementation.
2. **Pending Actions Identified**:
   * Team members to review the draft idea, user story, and specification documents:
     * User story: [VESSELS-US-001](../user-stories/VESSELS-US-001.md)
     * Specification: [VESSELS.01](../specifications/VESSELS.01-vessel-register.md)
   * Team members to set up their local development environments (JDK 25, Docker, GitHub Packages read token for Gradle and npm) and execute the example application locally.

#### 2. What I asked the AI agent
* "To summarize and document the outcomes of the meeeting"

#### 3. What the agent got wrong / Pitfalls encountered
* None encountered during this team sync session.

#### 4. How it was corrected
* N/A.

---

### Entry 3: Specification Consolidation & Backlog Alignment

* **Date**: 2026-11-12
* **Duration**: 0.5 hours
* **Target Milestone**: Session 3 — Component backlog reconciliation & specification consolidation (Process Rule 1).

#### 1. What was done
1. **Team Review Meeting (2026-11-12)**:
   * Held a team meeting to review the four user stories ([VESSELS-US-001](../user-stories/VESSELS-US-001.md), [VESSELS-US-002](../user-stories/VESSELS-US-002.md), [VESSELS-US-003](../user-stories/VESSELS-US-003.md), [VESSELS-US-004](../user-stories/VESSELS-US-004.md)) and the corresponding specifications drafted for the vessel component.
   * Decided to consolidate the 4 separate specification documents (VESSELS.01 to VESSELS.04) into a single, unified, canonical specification: [VESSELS.01-vessel-register.md](../specifications/VESSELS.01-vessel-register.md).
2. **Unified Architecture & Specification Reconciled**:
   * Consolidated the data model into 5 clean tables: reference tables `vessels.vessel_type` and `vessels.retirement_reason`, alongside business tables `vessels.vessel`, `vessels.license`, and `vessels.vessel_ownership`.
   * Reconciled dynamic license validity calculation (`effectiveStatus`: `VALID`, `EXPIRING_SOON`, `EXPIRED`, `NOT_YET_VALID`, `REVOKED`) and formal revocation.
   * Specified ownership transfer with dated periods (`vessel_ownership`) and live X-Road validation of the new owner's `isikukood`.
   * Formulated recorded retirement with atomic soft-delete, freeing registry codes for reuse, cascading license revocation, and freezing retired vessels against mutations.
   * Reconciled external registry failure policies: fail-open on lookups, fail-closed on ownership transfers.
   * Formulated 16 executable business test scenarios in Given-When-Then format covering all four functional areas.
3. **User Stories & Documentation Consistency**:
   * Aligned all four user story files with consistent tracing headers, persona definitions (`**As** / **I want** / **so that**`), and standardized HTTP error codes (`400 Bad Request`, `409 Conflict`, `502 Bad Gateway`).
   * Removed obsolete specification files (VESSELS.02, VESSELS.03, VESSELS.04).
   * Updated [SUMMARY.md](../SUMMARY.md) and [README.md](../../../README.md) to reflect the unified specification and full component scope.

#### 2. What I asked the AI agent
* "I want to merge that 4 specification files about vessel register into one file. I want to have this info consistent also with vessels user stories"

---

### Entry 4: Database Layer Testing, Backend API Implementation & Specification Re-validation

* **Date**: 2026-11-20 – 2026-11-21
* **Duration**: 6.0 hours
* **Target Milestone**: Sessions 2 & 3 — Testing the database layer created by Rocio, implementing the backend services and REST API with Mock X-Road integration, business/API tests, and specification re-validation.

#### 1. What was done
1. **Verification & Testing of the Database Layer (implemented by Rocio)**:
   * Reviewed the `vessels` database layer, Liquibase changesets, models, and repositories prepared by teammate Rocio.
   * Created [VesselDataLayerIT.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselDataLayerIT.java) (16 Testcontainers integration tests verifying schema migrations, reference seeds, `sys_*` trigger metadata, partial unique index reuse after soft-delete, CHECK/FK constraints, search/filter/sort, license revocation, expiring list exclusions, and ownership periods).
   * Created [LicenseEffectiveStatusTest.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/LicenseEffectiveStatusTest.java) (4 unit tests covering dynamic status derivation and 0/30/31-day boundary dates).
   * Documented database and backend testing workflows in [README.md](../../../README.md) and enabled per-test terminal output in `backend/build.gradle.kts`.
2. **Backend Services, REST API & Mock X-Road Integration**:
   * Implemented business services (`VesselService`, `LicenseService`, `OwnershipService`), DTO views (`LicenseView`, `LicenseType`), and HTTP-only controllers (`VesselController`, `VesselLicenseController`, `OwnershipController`, `LicenseController`) covering all 17 endpoints across `GET`, `POST`, `PUT`, and `DELETE`.
   * Integrated with `OwnerRegistryAdapter` (talking to the built-in `/mock-registry` locally and `forge-xroad` in `xroad` mode): enforced **fail-open** on owner read lookups (`GET /api/vessels/{id}/owner` and `GET /api/vessels/{id}/ownership/{ownershipId}/owner`) and **fail-closed** on ownership transfers (`POST /api/vessels/{id}/ownership`).
   * Created [VesselBusinessRulesIT.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselBusinessRulesIT.java) (32 service-level business tests with Mockito registry simulation) and [VesselApiIT.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselApiIT.java) (4 end-to-end HTTP lifecycle tests against `/mock-registry`), reaching **61 passing tests** in total.
3. **Manual Swagger UI Testing, Specification Re-validation & Refinements**:
   * Booted PostgreSQL and the backend locally (`./scripts/run-backend.sh`) and tested endpoints interactively via Swagger UI (`http://localhost:18440/swagger-ui.html`).
   * **Estonian `isikukood` Modulo-11 evaluation**: Verified whether the template's mock population registry codes (`38102130265`, `47503121234`, `50011020017`) pass the official EVS 585 Modulo-11 control-digit algorithm. Discovered that `38102130265` (remainder `6`) and `47503121234` (remainder `9`) use synthetic trailing digits; documented the design decision in [VESSELS.01-vessel-register.md](../specifications/VESSELS.01-vessel-register.md) and [README.md](../../../README.md) to retain 11-digit format validation (`^\d{11}$`) plus live registry verification.
   * **HTTP `DELETE` alignment**: Unified vessel retirement under REST soft-delete `DELETE /api/vessels/{id}` (`204 No Content`, removing the duplicate `POST /retirement` endpoint) and added `DELETE /api/vessels/{id}/licenses/{licenseId}` (`204 No Content`, plus `LicenseRepository.delete`) for cancelling scheduled `NOT_YET_VALID` licenses while protecting active/expired/revoked licenses with `409 Conflict` (updated [VESSELS-US-002.md](../user-stories/VESSELS-US-002.md) and Business Test 17 in `VESSELS.01`).
   * **Swagger UI polish**: Added `@ParameterObject` to query DTOs and updated [OpenApiConfig.java](../../../backend/src/main/java/ee/taltech/ite4120/config/OpenApiConfig.java) so Swagger UI displays `"Vessel and License Register — ITE4120"` as its main title and separates `Vessel and License Register (vessels)` (default view) from `Animals Register (course template)` (background reference package).

#### 2. What I asked the AI agent
* "I want to do testing of created database layer... more details should be in readme what to use to do testing"
* "my next task is to create backend for vessels application according to the documented specification and database layer. also i need to have in mind that my app should talk with mock x-road interface"
* "please run that tests i want to see in terminal how it looks like when testing is being done"
* "please run database and backend i want to talk to it and test with swagger"
* Asked why Swagger UI returned empty data when default placeholder parameters were submitted, how `owner_isikukood` avoids storing personal details locally, whether Estonian `isikukood` checksum validation could be added, and requested a full consistency audit between User Stories, Specification, Database, and API (including `GET`, `POST`, `PUT`, `DELETE` and Swagger UI title/package grouping).

#### 3. What the agent got wrong / Pitfalls encountered
* **`QueryParams.setSort` signature**: In `VesselDataLayerIT`, `setSort("passwordHash")` initially failed to compile because the Helex `QueryParams.setSort` method takes `List<String>` rather than a single `String`.
* **Cascading license revocation constraint on retirement**: `LicenseRepository.revokeAllValidForVessel` initially filtered only by `valid_until >= asOfDate`; if a vessel held a future-issued license (`issued_on > revoked_on`), revoking it violated `license_revoked_chk` (`revoked_on >= issued_on`).
* **Swagger UI `@ModelAttribute` default placeholders**: Without `@ParameterObject`, SpringDoc rendered `VesselQueryParams` in Swagger UI as a single JSON input pre-populated with `"textContains": "string"`, `"typeCode": "string"`, `"retired": true`, and `"limit": 0`, which caused "Try it out" to return an empty list until cleared.
* **Same-day ownership transfer boundary**: `OwnershipService.transfer` initially checked `!transferDate.isAfter(current.getOwnedFrom())` (`>`), which prevented transferring a vessel on the same day it was registered (`owned_from = today`), even though `VESSELS-US-003`, `VESSELS.01`, and the SQL constraint `vessel_ownership_dates_chk` allow `owned_until >= owned_from`.

#### 4. How it was corrected
* Updated the test call to `p.setSort(List.of("passwordHash"))`.
* Added `and issued_on <= ?` to `LicenseRepository.revokeAllValidForVessel` and `and l.issued_on <= ?` to `countExpiring`/`listExpiring`.
* Annotated `@ModelAttribute` parameters with `@ParameterObject` in `VesselController` and `LicenseController` so Swagger UI renders individual empty form fields.
* Aligned `OwnershipService.transfer` to check `transferDate.isBefore(current.getOwnedFrom())`, matching the specification and SQL constraint and allowing same-day transfers.

---

### Entry 5: Frontend Testing & Error Handling, Direct Edit UX, Live Registration Validation & SchemaSpy Visualization

* **Date**: 2026-11-23
* **Duration**: 3.5 hours
* **Target Milestone**: Session 4 — Frontend contract & error-handling tests unified in Gradle, direct Edit action on the main vessel list, live X-Road owner validation on vessel registration, code cleanup, and SchemaSpy database visualization.

#### 1. What was done
1. **Documentation vs. Source Code Consistency & Repository Cleanup**:
   * Audited all documentation files ([VESSELS.01-vessel-register.md](../specifications/VESSELS.01-vessel-register.md), [VESSELS-US-001.md](../user-stories/VESSELS-US-001.md)–[VESSELS-US-004.md](../user-stories/VESSELS-US-004.md), [README.md](../../../README.md)) against the backend and frontend source code on `feature/vessels-frontend`.
   * Updated the state of `VESSELS.01` and all four user stories from `Proposed` to `Implemented` and ensured `DELETE /api/vessels/{id}` (`204 No Content` soft delete) is consistently referenced across all sections.
   * Removed two unused repository methods (`VesselRepository.loadActive(Long)` and `LicenseRepository.listCurrentlyValidByVesselId(...)`) after confirming no service or API endpoint called them.
2. **Direct `Edit` Button in the Main Vessel List View**:
   * Added a locked `Actions` column to [VesselList.tsx](../../../frontend/src/pages/vessels/VesselList.tsx) with an **Edit** button (`AppButtonSecondary` with `<EditOutlined />`) for active vessels that navigates directly to `/vessels/{id}?edit=1` (and a **View** button for retired vessels), plus **View details** / **Edit vessel** buttons in the side detail panel.
   * Updated [VesselDetail.tsx](../../../frontend/src/pages/vessels/VesselDetail.tsx) (`useSearchParams`) so opening `/vessels/{id}?edit=1` enters `ResourceForm` directly in `'edit'` mode for active vessels without requiring an extra click.
3. **Frontend Automated Testing & Error-Handling Suite Unified in Gradle**:
   * Created [vesselsApi.test.ts](../../../frontend/src/vesselsApi.test.ts) (13 unit & API contract tests covering `isCurrentlyValid`, `isRetired`, bearer auth headers, query building, `POST`/`PUT`/`DELETE` requests, and RFC 9457 `application/problem+json` error handling across `400`, `404`, `409`, `502`, and non-JSON `503` responses).
   * Integrated the frontend test suite and TypeScript typecheck (`tsc -b`) directly into Gradle via JUnit 5 `@TestFactory` in [VesselFrontendTest.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselFrontendTest.java), bringing the unified `./gradlew test` suite to **77 passing tests**.
4. **Live X-Road Owner Verification on Vessel Registration (`POST /api/vessels` & `PUT /api/vessels/{id}`)**:
   * Extended [VesselService.java](../../../backend/src/main/java/ee/taltech/ite4120/vessels/service/VesselService.java) (`verifyOwnerExistsInRegistry`) so that providing an `ownerIsikukood` on initial vessel registration (or first-owner assignment via `PUT`) verifies the personal code live against the population registry (`OwnerRegistryAdapter`) before opening a database transaction (**fail-closed**: `400 Bad Request` if the code is not recognized by the registry, `502 Bad Gateway` if the registry is down).
   * Updated [VESSELS-US-001.md](../user-stories/VESSELS-US-001.md), [VESSELS.01-vessel-register.md](../specifications/VESSELS.01-vessel-register.md), [README.md](../../../README.md), [VesselBusinessRulesIT.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselBusinessRulesIT.java), and [VesselApiIT.java](../../../backend/src/test/java/ee/taltech/ite4120/vessels/VesselApiIT.java).
5. **SchemaSpy & Mermaid ER Database Visualization (`vessels` schema only)**:
   * Added GitHub Actions workflow [schemaspy.yml](../../../.github/workflows/schemaspy.yml) and local helper scripts ([run-schemaspy.sh](../../../scripts/run-schemaspy.sh), [run-schemaspy.ps1](../../../scripts/run-schemaspy.ps1)) running `schemaspy/schemaspy:latest` with `-s vessels` (visualizing strictly the 5 tables of the `vessels` schema, uploading the `schemaspy-vessels-report` artifact, and deploying to GitHub Pages).
   * Embedded a native Mermaid `erDiagram` of the `vessels` schema directly in [README.md](../../../README.md) and [VESSELS.01-vessel-register.md](../specifications/VESSELS.01-vessel-register.md).


#### 3. What the agent got wrong / Pitfalls encountered
* **TypeScript `.ts` import extension in `vesselsApi.test.ts`**: Node's native `--experimental-strip-types` runner requires the explicit `./vesselsApi.ts` extension on relative imports, which initially caused `tsc -b` to fail with `TS5097` until `"allowImportingTsExtensions": true` was enabled in `frontend/tsconfig.json`.
* **Substring assertion in `VesselFrontendTest`**: Asserting `.doesNotContain("/retirement")` on `vesselsApi.ts` initially matched `/api/vessels/retirement-reasons`; refined the assertion to check `.doesNotContain("/retirement`")` and `.doesNotContain("/retirement'")`.
* **Mockito invocation count after adding registration owner verification**: In `VesselBusinessRulesIT.ownerLookupGoesThroughTheRegistryAdapter`, calling `vessels.create(v)` with an owner and then `vessels.owner(id)` invoked `registry.lookup(OWNER_A)` twice instead of once, requiring `verify(registry, times(2)).lookup(OWNER_A)`.

#### 4. How it was corrected
* Enabled `"allowImportingTsExtensions": true` alongside `"noEmit": true` in `frontend/tsconfig.json` so both `node --experimental-strip-types --test` and `tsc -b` succeed without errors.
* Tightened the string check in `VesselFrontendTest` and updated the Mockito verification count in `VesselBusinessRulesIT`, achieving 77/77 passing tests in `./gradlew test`.

---

### Time Summary

| Milestone / Task | Date | Hours |
| :--- | :--- | :--- |
| Session 1: Environment verification, token configuration, template test, docs draft | 2026-10-07 | 2.5 h |
| Session 1: Team synchronization call (technicalities, next steps, action items) | 2026-10-22 | 1.0 h |
| Session 3: Specification consolidation & backlog alignment (team meeting on 12.11) | 2026-11-12 | 0.5 h |
| Sessions 2 & 3: Database layer testing, backend API, Mock X-Road integration & re-validation | 2026-11-20 – 2026-11-21 | 6.0 h |
| Session 4: Frontend testing & error handling in Gradle, Edit button UX, live registration owner check & SchemaSpy | 2026-11-23 | 3.5 h |
| **Total** | | **13.5 h** |

