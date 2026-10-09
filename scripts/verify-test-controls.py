#!/usr/bin/env python3
"""Exercise the real Gradle test policy without engine classes or game assets.

Definitive verification tooling (2026-10-09), AGPL v3; see LICENSE.
"""

from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def main():
    root = Path(__file__).resolve().parents[1]
    output = root / "build" / "test-controls"
    output.mkdir(parents=True, exist_ok=True)
    # A fresh project isolates reports and prevents cached results becoming passes.
    with tempfile.TemporaryDirectory(prefix="fixture-", dir=output) as temporary:
        fixture = Path(temporary)
        (fixture / "settings.gradle").write_text("rootProject.name = 'test-controls-fixture'\n")
        # Applying the production policy keeps this a behavioral check of its gate.
        (fixture / "build.gradle").write_text("""
plugins { id 'java' }
repositories { mavenCentral() }
dependencies {
  testImplementation platform('org.junit:junit-bom:6.0.0')
  testImplementation 'org.junit.jupiter:junit-jupiter'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
apply from: providers.gradleProperty('policyFile').get()
""")
        source = fixture / "src/test/java/legend/game"
        source.mkdir(parents=True)
        (source / "EngineBootTest.java").write_text("""
package legend.game;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
class EngineBootTest {
  @Test void probe() throws Exception {
    Files.writeString(Path.of("probe-ran"), "headless fixture only");
  }
}
""")
        # If this excluded sandbox executes, the verification must fail.
        (source / "ExampleTest.java").write_text("""
package legend.game;
import org.junit.jupiter.api.Test;
class ExampleTest {
  @Test void sandboxMustStayExcluded() {
    throw new AssertionError("Interactive sandbox was selected");
  }
}
""")
        cases = [
            ("default-off", [], False, False, None),
            ("explicit-false", ["-PrunTests=false"], False, False, None),
            ("missing-private-files", ["-PrunTests"], False, False, "files/version"),
            ("bare-opt-in", ["-PrunTests"], True, True, None),
            ("true-opt-in", ["-PrunTests=true"], True, True, None),
            ("invalid-value", ["-PrunTests=invalid"], False, False, "runTests must be"),
            ("sandbox-excluded", ["-PrunTests", "--tests", "legend.game.ExampleTest"], True, False, "No tests found"),
        ]
        for name, flags, extracted, should_run, error in cases:
            marker = fixture / "probe-ran"
            marker.unlink(missing_ok=True)
            version = fixture / "files/version"
            version.parent.mkdir(exist_ok=True)
            if extracted:
                # Synthetic marker in a fixture project, never actual game data.
                version.write_text("fixture\n")
            else:
                version.unlink(missing_ok=True)
            result = subprocess.run(
                [str(root / "gradlew"), "-p", str(fixture), "--no-daemon", "--console=plain",
                 "clean", "test", f"-PpolicyFile={root / 'gradle/gameplay-tests.gradle'}", *flags],
                cwd=fixture, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            )
            (output / f"{name}.txt").write_text(result.stdout)
            if error:
                if result.returncode == 0 or error not in result.stdout or marker.exists():
                    raise RuntimeError(f"{name}: expected safe rejection; see {output / (name + '.txt')}")
            elif result.returncode != 0 or marker.exists() != should_run:
                raise RuntimeError(f"{name}: wrong execution result; see {output / (name + '.txt')}")
            elif should_run:
                reports = list((fixture / "build/test-results/test").glob("TEST-*.xml"))
                if len(reports) != 1:
                    raise RuntimeError(f"{name}: expected one headless test report")
                report = ET.parse(reports[0]).getroot()
                if (report.get("name") != "legend.game.EngineBootTest" or
                        any(report.get(key) != value for key, value in
                            {"tests": "1", "failures": "0", "errors": "0", "skipped": "0"}.items())):
                    raise RuntimeError(f"{name}: unexpected test report: {report.attrib}")
            elif ":test SKIPPED" not in result.stdout:
                raise RuntimeError(f"{name}: test task was not explicitly skipped")
            print(f"PASS {name}", flush=True)
        print("Seven test-control scenarios passed; no engine code or game assets loaded.", flush=True)


if __name__ == "__main__":
    main()
