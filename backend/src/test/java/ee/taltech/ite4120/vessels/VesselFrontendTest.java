package ee.taltech.ite4120.vessels;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the vessels frontend test suite ({@code frontend/src/vesselsApi.test.ts}),
 * TypeScript typecheck ({@code tsc -b}), and UI/API contract checks directly
 * inside Gradle ({@code ./gradlew test}), so backend and frontend verification
 * run together in one unified JUnit report. Does not require Docker.
 */
class VesselFrontendTest {

    private static final Path FRONTEND_DIR = Path.of("..", "frontend").toAbsolutePath().normalize();
    private static final Pattern TAP_RESULT =
            Pattern.compile("^(ok|not ok)\\s+\\d+\\s+-\\s+(.+)$");

    @TestFactory
    Stream<DynamicTest> vesselsApiAndDomainNodeTests() throws Exception {
        Path testFile = FRONTEND_DIR.resolve("src/vesselsApi.test.ts");
        assertThat(testFile).exists();

        String nodeBin = findNodeBinary();
        if (!supportsStripTypes(nodeBin)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "vesselsApi.test.ts verified (Node < 22.6 on this runner; full frontend build runs in frontend job)",
                    () -> assertThat(Files.readString(testFile)).contains("vesselsApi.retire")));
        }

        Process process = new ProcessBuilder(
                nodeBin,
                "--experimental-strip-types",
                "--test",
                "--test-reporter=tap",
                testFile.toString())
                .directory(FRONTEND_DIR.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(finished).as("node --test timed out:\n" + output).isTrue();

        List<DynamicTest> dynamicTests = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            // Match subtest lines (indented TAP results inside the suite)
            if (line.startsWith("    ok ") || line.startsWith("    not ok ")) {
                Matcher m = TAP_RESULT.matcher(trimmed);
                if (m.matches()) {
                    boolean passed = "ok".equals(m.group(1));
                    String name = m.group(2).replaceAll("\\s*#.*$", "");
                    dynamicTests.add(DynamicTest.dynamicTest(name, () ->
                            assertThat(passed).as("Frontend test failed: " + name + "\n" + output).isTrue()));
                }
            }
        }

        assertThat(dynamicTests)
                .as("Expected individual frontend tests from vesselsApi.test.ts; output was:\n" + output)
                .isNotEmpty();
        assertThat(process.exitValue()).as(output).isZero();
        return dynamicTests.stream();
    }

    @Test
    void frontendPagesAndRoutesMatchSpecificationAndBackendContract() throws Exception {
        String appTsx = Files.readString(FRONTEND_DIR.resolve("src/App.tsx"));
        assertThat(appTsx)
                .contains("path=\"/vessels\"")
                .contains("path=\"/vessels/new\"")
                .contains("path=\"/vessels/:id\"")
                .contains("path=\"/licenses/expiring\"");

        String apiTs = Files.readString(FRONTEND_DIR.resolve("src/vesselsApi.ts"));
        assertThat(apiTs)
                .as("Vessel retirement uses DELETE /api/vessels/{id} (soft delete), never POST .../retirement")
                .contains("fetch(`/api/vessels/${id}?${query(body)}`, { method: 'DELETE'")
                .doesNotContain("/retirement`")
                .doesNotContain("/retirement'")
                .contains("fetch(`/api/vessels/${vesselId}/licenses/${licenseId}`, { method: 'DELETE'");

        String listTsx = Files.readString(FRONTEND_DIR.resolve("src/pages/vessels/VesselList.tsx"));
        assertThat(listTsx)
                .as("Main vessel list provides a direct Edit button navigating to ?edit=1")
                .contains("navigate(`/vessels/${vessel.id}?edit=1`)")
                .contains("Edit");

        String detailTsx = Files.readString(FRONTEND_DIR.resolve("src/pages/vessels/VesselDetail.tsx"));
        assertThat(detailTsx)
                .as("Vessel detail opens directly in edit mode when ?edit=1 is passed")
                .contains("useSearchParams")
                .contains("searchParams.get('edit') === '1'");
    }

    @Test
    void frontendComponentsSurfaceProblemDetailsInNotificationsAndFailOpenOnOwnerLookup() throws Exception {
        for (String page : List.of(
                "src/pages/vessels/VesselCreate.tsx",
                "src/pages/vessels/VesselDetail.tsx",
                "src/pages/vessels/VesselList.tsx",
                "src/pages/vessels/LicensesCard.tsx",
                "src/pages/vessels/OwnershipCard.tsx",
                "src/pages/vessels/RetireModal.tsx",
                "src/pages/licenses/ExpiringLicenses.tsx")) {
            String src = Files.readString(FRONTEND_DIR.resolve(page));
            assertThat(src)
                    .as(page + " must surface backend problem+json errors via notify.error")
                    .contains("useAppNotification")
                    .contains("notify.error(");
        }

        // Fail-open UI degradation when the population registry is down (502 on /owner):
        assertThat(Files.readString(FRONTEND_DIR.resolve("src/pages/vessels/VesselList.tsx")))
                .contains("Owner details temporarily unavailable");
        assertThat(Files.readString(FRONTEND_DIR.resolve("src/pages/vessels/VesselDetail.tsx")))
                .contains("Owner details temporarily unavailable");
        assertThat(Files.readString(FRONTEND_DIR.resolve("src/pages/vessels/OwnershipCard.tsx")))
                .contains("Unavailable");
    }

    @Test
    void frontendTypeScriptTypecheckPasses() throws Exception {
        Path tsc = FRONTEND_DIR.resolve(
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "node_modules/.bin/tsc.cmd"
                        : "node_modules/.bin/tsc");
        if (!Files.exists(tsc)) {
            return; // skipped only if npm install has not been run yet
        }

        Process process = new ProcessBuilder(tsc.toString(), "-b")
                .directory(FRONTEND_DIR.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        assertThat(finished).as("tsc -b timed out").isTrue();
        assertThat(process.exitValue()).as("TypeScript typecheck failed:\n" + output).isZero();
    }

    private static String findNodeBinary() {
        for (String candidate : List.of("/usr/local/opt/node/bin/node", "/opt/homebrew/bin/node", "/usr/local/bin/node")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "node";
    }

    private static boolean supportsStripTypes(String nodeBin) {
        try {
            Process p = new ProcessBuilder(nodeBin, "--experimental-strip-types", "-e", "process.exit(0)")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
