from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]


class ChangelogTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        for filename in (
            "towncrier.toml",
            ".github/scripts/changelog/template.md.jinja",
            ".github/scripts/update-changelog-for-release.sh",
        ):
            destination = self.repo / filename
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO_ROOT / filename, destination)
        (self.repo / "changelog.d").mkdir()
        self.write("changelog.d/README.md", "Contributor guidance.\n")
        self.write(
            "dependencyManagement/build.gradle.kts",
            'val otelSdkVersion = "1.66.0"\n',
        )
        self.write(
            "CHANGELOG.md",
            "# Changelog\n\n## Unreleased\n\n"
            "Release notes for upcoming changes are in [changelog.d](changelog.d).\n\n"
            "<!-- towncrier release notes start -->\n\n"
            "## Version 2.32.0 (2026-10-03)\n\nPrevious release notes.\n",
        )
        self.run_command("git", "init", "--quiet", "--initial-branch=main")
        self.run_command("git", "config", "user.name", "Changelog test")
        self.run_command("git", "config", "user.email", "changelog-test@example.invalid")
        self.run_command("git", "config", "core.autocrlf", "false")
        self.commit()

    def write(self, filename, content):
        path = self.repo / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")

    def run_command(self, *args, check=True, env=None):
        command_env = os.environ.copy()
        command_env["PYTHONIOENCODING"] = "utf-8"
        command_env["PATH"] = (
            str(Path(sys.executable).parent) + os.pathsep + command_env["PATH"]
        )
        if env:
            command_env.update(env)
        executable = shutil.which(args[0], path=command_env["PATH"])
        self.assertIsNotNone(executable, f"Command not found: {args[0]}")
        result = subprocess.run(
            (executable, *args[1:]),
            cwd=self.repo,
            env=command_env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        if check:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def commit(self):
        self.run_command("git", "add", ".")
        self.run_command(
            "git",
            "-c",
            "commit.gpgsign=false",
            "-c",
            "core.hooksPath=",
            "commit",
            "--quiet",
            "-m",
            "Test fixture",
        )

    def prepare(self, version="3.0.0", *, check=True):
        return self.run_command(
            "bash",
            "-e",
            ".github/scripts/update-changelog-for-release.sh",
            version,
            "2026-11-01",
            check=check,
        )

    def changelog(self):
        return (self.repo / "CHANGELOG.md").read_text(encoding="utf-8")

    def extract_section(self, version):
        return self.run_command(
            "bash",
            "-c",
            f'sed -n "0,/^## Version {version} /d;/^## Version /q;p" CHANGELOG.md',
        ).stdout

    def synchronize(self, version, section):
        self.write("tmp/changelog-section.md", section)
        # Keep the release workflow's handoff files inside this fixture.
        script = (
            REPO_ROOT / ".github/scripts/merge-change-log-after-release.sh"
        ).read_text(encoding="utf-8")
        self.write("merge.sh", script.replace("/tmp/", "./tmp/"))
        self.run_command(
            "bash",
            "-e",
            "merge.sh",
            env={"VERSION": version, "RELEASE_DATE": "2026-11-02"},
        )

    def test_preview_categories_links_and_multiline_notes(self):
        for number, (category, heading) in enumerate(
            (
                ("breaking", "⚠️ Breaking changes"),
                ("alpha-breaking", "⚠️ Breaking changes to non-stable APIs"),
                ("deprecation", "🚫 Deprecations"),
                ("javaagent", "🌟 New javaagent instrumentation"),
                ("library", "🌟 New library instrumentation"),
                ("enhancement", "📈 Enhancements"),
                ("bugfix", "🛠️ Bug fixes"),
            ),
            start=1,
        ):
            self.write(f"changelog.d/{number}.{category}.md", f"Note for {category}.\n")
        self.write("changelog.d/7.bugfix.1.md", "Another fix in the same PR.\n")
        self.write(
            "changelog.d/+migration.breaking.md",
            "Remove `old`.\nUse `replacement`.\n\n"
            "Migration steps:\n\n- Update the configuration.\n- Restart the agent.\n",
        )
        self.commit()
        before = self.changelog()
        preview = self.run_command(
            sys.executable,
            "-m",
            "towncrier",
            "build",
            "--draft",
            "--version",
            "3.0.0",
            "--date",
            "2026-11-01",
        ).stdout
        self.assertTrue(preview.startswith("## Version 3.0.0 (2026-11-01)\n\n"))
        self.assertEqual(preview.count("\n### "), 7)
        self.assertLess(
            preview.index("### ⚠️ Breaking changes\n"),
            preview.index("### ⚠️ Breaking changes to non-stable APIs\n"),
        )
        self.assertLess(preview.index("### 🚫 Deprecations\n"), preview.index("### 🛠️ Bug fixes\n"))
        self.assertIn(
            "  ([#7](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/7))",
            preview,
        )
        self.assertIn("- Another fix in the same PR.\n", preview)
        self.assertIn("- Remove `old`.\n  Use `replacement`.\n", preview)
        self.assertIn("\n  - Update the configuration.\n", preview)
        self.assertNotIn("#+migration", preview)
        self.assertIn("\n\n### 🚫 Deprecations\n\n", preview)
        self.assertEqual(self.changelog(), before)
        self.assertEqual(self.run_command("git", "status", "--porcelain").stdout, "")

    def test_release_accepts_numeric_suffixes_and_orphan_fragments(self):
        self.write("changelog.d/12345.bugfix.1.md", "A fix with a numeric suffix.\n")
        self.write("changelog.d/+pending.bugfix.md", "A fix awaiting a PR number.\n")
        self.commit()
        self.prepare()
        result = self.changelog()
        self.assertIn("- A fix with a numeric suffix.\n", result)
        self.assertIn("- A fix awaiting a PR number.\n", result)
        self.assertIn(
            "[#12345](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/12345)",
            result,
        )
        self.assertNotIn("/pull/+", result)
        self.assertFalse((self.repo / "changelog.d/12345.bugfix.1.md").exists())
        self.assertFalse((self.repo / "changelog.d/+pending.bugfix.md").exists())

    def test_nonnumeric_pr_identifier_fails_without_changelog_changes(self):
        self.write("changelog.d/fix-http.bugfix.md", "A fix without a PR number.\n")
        self.write("changelog.d/1.bugfix.md", "A valid fix.\n")
        self.commit()
        before = self.changelog()
        for draft in (True, False):
            with self.subTest(draft=draft):
                if draft:
                    result = self.run_command(
                        sys.executable,
                        "-m",
                        "towncrier",
                        "build",
                        "--draft",
                        "--version",
                        "3.0.0",
                        check=False,
                    )
                else:
                    result = self.prepare(check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Issue name 'fix-http' does not match", result.stderr)
                self.assertEqual(self.changelog(), before)
                self.assertEqual(
                    self.run_command("git", "status", "--porcelain").stdout, ""
                )

    def test_prepare_preserves_history_and_stages_consumed_fragments(self):
        self.write("changelog.d/1.bugfix.md", "Fix missing spans.\n")
        self.commit()
        history = self.changelog().split("## Version 2.32.0", 1)[1]
        self.prepare()
        result = self.changelog()
        self.assertIn("## Unreleased\n", result)
        self.assertIn(
            "Release notes for upcoming changes are in [changelog.d](changelog.d).\n",
            result,
        )
        self.assertIn("## Version 3.0.0 (2026-11-01)\n\n", result)
        self.assertIn("This release targets the OpenTelemetry SDK 1.66.0.\n", result)
        self.assertIn("many artifacts have the `-alpha` suffix", result)
        self.assertIn("- Fix missing spans.\n", result)
        self.assertEqual(result.split("## Version 2.32.0", 1)[1], history)
        self.assertFalse((self.repo / "changelog.d/1.bugfix.md").exists())
        self.assertTrue((self.repo / "changelog.d/README.md").exists())
        self.assertIn(
            "D\tchangelog.d/1.bugfix.md",
            self.run_command("git", "diff", "--cached", "--name-status").stdout,
        )
        self.assertEqual(self.run_command("git", "diff", "--name-only").stdout, "")

    def test_patch_release_after_minor_and_previous_patch(self):
        self.write("changelog.d/1.bugfix.md", "Original fix.\n")
        self.commit()
        self.prepare()
        self.assertIn("## Unreleased\n", self.changelog())
        for version, number in (("3.0.1", 2), ("3.0.2", 3)):
            with self.subTest(version=version):
                previous = self.changelog().split("## Version ", 1)[1]
                self.write(f"changelog.d/{number}.bugfix.md", "Backported fix.\n")
                self.commit()
                self.prepare(version)
                self.assertIn("## Unreleased\n", self.changelog())
                self.assertIn(f"## Version {version} (2026-11-01)\n", self.changelog())
                self.assertTrue(self.changelog().endswith("## Version " + previous))
                self.assertIn("- Backported fix.\n", self.extract_section(version))
                self.assertFalse((self.repo / f"changelog.d/{number}.bugfix.md").exists())

    def test_post_release_corrections_preserve_pending_fragments(self):
        self.write("changelog.d/1.bugfix.md", "Original fix.\n")
        self.commit()
        self.prepare()
        section = self.extract_section("3.0.0").replace("Original fix.", "Corrected note.")
        self.write("changelog.d/2.enhancement.md", "Next release's enhancement.\n")
        self.synchronize("3.0.0", section)
        self.assertIn("## Version 3.0.0 (2026-11-02)\n", self.changelog())
        self.assertIn("- Corrected note.\n", self.changelog())
        self.assertNotIn("- Original fix.", self.changelog())
        self.assertIn("## Unreleased\n", self.changelog())
        self.assertTrue((self.repo / "changelog.d/2.enhancement.md").exists())

    def test_patch_synchronization_preserves_main_history(self):
        self.write("changelog.d/1.bugfix.md", "Original fix.\n")
        self.commit()
        self.prepare()
        main = self.changelog()
        self.write("changelog.d/2.bugfix.md", "Backported fix.\n")
        self.commit()
        self.prepare("3.0.1")
        section = self.extract_section("3.0.1")
        self.write("CHANGELOG.md", main)
        self.write("changelog.d/3.enhancement.md", "Next release's enhancement.\n")
        self.synchronize("3.0.1", section)
        self.assertIn("## Version 3.0.1 (2026-11-02)\n", self.changelog())
        self.assertIn("## Unreleased\n", self.changelog())
        self.assertTrue(self.changelog().endswith(main.split("## Version ", 1)[1]))
        self.assertTrue((self.repo / "changelog.d/3.enhancement.md").exists())

    def test_duplicate_version_fails_before_consuming_pending_fragments(self):
        self.write("changelog.d/1.bugfix.md", "Original fix.\n")
        self.commit()
        self.prepare()
        self.write("changelog.d/2.bugfix.md", "Pending fix.\n")
        before = self.changelog()
        result = self.run_command(
            "bash",
            "-e",
            ".github/scripts/update-changelog-for-release.sh",
            "3.0.0",
            "2026-11-02",
            check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("already contains version 3.0.0", result.stderr)
        self.assertEqual(self.changelog(), before)
        self.assertTrue((self.repo / "changelog.d/2.bugfix.md").exists())

    def test_preparation_accepts_only_version_and_date(self):
        before = self.changelog()
        for args in ((), ("3.0.0",), ("3.0.0", "2026-11-01", "unexpected")):
            with self.subTest(args=args):
                result = self.run_command(
                    "bash",
                    "-e",
                    ".github/scripts/update-changelog-for-release.sh",
                    *args,
                    check=False,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("<version> <date>", result.stderr)
                self.assertEqual(self.changelog(), before)

    def test_invalid_marker_and_unknown_fragment_fail_without_changelog_changes(self):
        before = self.changelog()
        self.write("CHANGELOG.md", before.replace("<!-- towncrier release notes start -->", ""))
        result = self.prepare(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly one Towncrier insertion marker", result.stderr)
        self.write("CHANGELOG.md", before)
        self.write("changelog.d/1.unknown.md", "An invalid category.\n")
        self.commit()
        result = self.prepare(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.changelog(), before)
        self.assertTrue((self.repo / "changelog.d/1.unknown.md").exists())


if __name__ == "__main__":
    unittest.main()
