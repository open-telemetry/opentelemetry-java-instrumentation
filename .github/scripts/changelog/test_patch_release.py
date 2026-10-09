import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/prepare-patch-release.yml"
MARKER = "<!-- towncrier release notes start -->"


def workflow_script(name):
    match = re.search(
        rf"      - name: {re.escape(name)}\n        run: \|\n((?:          [^\n]*\n|\n)+)",
        WORKFLOW.read_text(encoding="utf-8"),
    )
    if match is None:
        raise AssertionError(f"Could not find workflow script: {name}")
    return textwrap.dedent(match.group(1))


class PatchReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.changelog = self.root / "CHANGELOG.md"
        self.changelog.write_text(
            "# Changelog\n\n## Unreleased\n\n### Bug fixes\n\n- Fix missing spans.\n",
            encoding="utf-8",
        )

    def run_step(self, name, branch="release/v2.32.x"):
        return subprocess.run(
            [
                "bash",
                "-e",
                "-o",
                "pipefail",
                "-c",
                f"GITHUB_REF_NAME={branch}\nVERSION=2.32.1\n" + workflow_script(name),
            ],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

    def enable_towncrier(self):
        shutil.copyfile(ROOT / "towncrier.toml", self.root / "towncrier.toml")
        scripts = self.root / ".github/scripts"
        (scripts / "changelog").mkdir(parents=True)
        shutil.copyfile(
            ROOT / ".github/scripts/changelog/template.md.jinja",
            scripts / "changelog/template.md.jinja",
        )
        shutil.copy2(
            ROOT / ".github/scripts/update-changelog-for-release.sh",
            scripts / "update-changelog-for-release.sh",
        )
        dependency_management = self.root / "dependencyManagement"
        dependency_management.mkdir()
        (dependency_management / "build.gradle.kts").write_text(
            'val otelSdkVersion = "1.66.0"\n', encoding="utf-8"
        )
        self.changelog.write_text(
            f"# Changelog\n\n## Unreleased\n\n{MARKER}\n\n"
            "## Version 2.32.0 (2026-10-03)\n\nPrevious release.\n",
            encoding="utf-8",
        )
        subprocess.run(["git", "init", "--quiet"], cwd=self.root, check=True)

    def test_towncrier_setup_and_install_are_conditional(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        condition = "        if: ${{ hashFiles('towncrier.toml') != '' }}\n"
        self.assertRegex(
            workflow,
            r"      - uses: actions/setup-python@[^\n]+\n" + re.escape(condition),
        )
        self.assertIn("      - name: Install Towncrier\n" + condition, workflow)

    def test_legacy_prerequisites(self):
        result = self.run_step("Verify prerequisites")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_legacy_requires_unreleased_section(self):
        self.changelog.write_text("# Changelog\n", encoding="utf-8")
        result = self.run_step("Verify prerequisites")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing the Unreleased section", result.stdout)

    def test_towncrier_prerequisites(self):
        self.enable_towncrier()
        result = self.run_step("Verify prerequisites")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_towncrier_requires_marker_even_with_unreleased_section(self):
        self.enable_towncrier()
        self.changelog.write_text("# Changelog\n\n## Unreleased\n", encoding="utf-8")
        result = self.run_step("Verify prerequisites")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing the Towncrier insertion marker", result.stdout)

    def test_rejects_non_release_branches_for_both_formats(self):
        for towncrier in (False, True):
            with self.subTest(towncrier=towncrier):
                if towncrier:
                    self.enable_towncrier()
                result = self.run_step("Verify prerequisites", branch="main")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("only be run against release branches", result.stdout)

    def test_legacy_release_preserves_notes_and_ignores_fragments(self):
        fragments = self.root / "changelog.d"
        fragments.mkdir()
        fragment = fragments / "1.bugfix.md"
        fragment.write_text("Do not render this fragment.\n", encoding="utf-8")
        original = self.changelog.read_text(encoding="utf-8")
        result = self.run_step("Update the change log with the approximate release date")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        updated = self.changelog.read_text(encoding="utf-8")
        self.assertRegex(updated, r"## Version 2\.32\.1 \(\d{4}-\d{2}-\d{2}\)")
        self.assertEqual(
            re.sub(r"## Version 2\.32\.1 \(\d{4}-\d{2}-\d{2}\)", "## Unreleased", updated),
            original,
        )
        self.assertTrue(fragment.is_file())
        self.assertNotIn("Do not render this fragment.", updated)

    def test_towncrier_release_renders_and_consumes_fragments(self):
        self.enable_towncrier()
        fragments = self.root / "changelog.d"
        fragments.mkdir()
        fragment = fragments / "1.bugfix.md"
        fragment.write_text("Fix missing spans.\n", encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        result = self.run_step("Update the change log with the approximate release date")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        updated = self.changelog.read_text(encoding="utf-8")
        self.assertRegex(updated, r"## Version 2\.32\.1 \(\d{4}-\d{2}-\d{2}\)")
        self.assertIn("This release targets the OpenTelemetry SDK 1.66.0.", updated)
        self.assertIn("- Fix missing spans.", updated)
        self.assertIn("## Version 2.32.0 (2026-10-03)\n\nPrevious release.", updated)
        self.assertIn("## Unreleased", updated)
        self.assertIn(MARKER, updated)
        self.assertFalse(fragment.exists())


if __name__ == "__main__":
    unittest.main()
