# Work diary · Aleksander Anchimiuk

> Student: **Aleksander Anchimiuk** (`aleksanderanchimiuk@gmail.com`)  
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

### Time Summary

| Milestone / Task | Date | Hours |
| :--- | :--- | :--- |
| Session 1: Environment verification, token configuration, template test, docs draft | 2026-10-07 | 2.5 h |
| **Total** | | **2.5 h** |

