import contextlib
import importlib.util
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "release_config", Path(__file__).with_name("release-config.py")
)
release_config = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(release_config)


def run_command(args, cwd, env=None):
    return subprocess.run(
        args,
        cwd=cwd,
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
        creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
    )


def workflow_script(filename, step):
    text = (ROOT / ".github" / "workflows" / filename).read_text(encoding="utf-8")
    match = re.search(
        rf"^      (?:- |  )name: {re.escape(step)}\n.*?^        run: \|\n"
        r"((?:          [^\n]*\n|\n)+)",
        text,
        re.MULTILINE | re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"missing script for {filename}: {step}")
    return "\n".join(line[10:] for line in match.group(1).splitlines())


class ReleaseConfigTest(unittest.TestCase):
    def test_development_streams(self):
        for ref, version in (("main", "3.0.0-SNAPSHOT"), ("2.x", "2.33.0-SNAPSHOT")):
            with self.subTest(ref=ref):
                config = release_config.development_config(ref, version)
                self.assertEqual(config["DEVELOPMENT_BRANCH"], ref)
                self.assertEqual(
                    config["NEXT_VERSION"],
                    "3.1.0-SNAPSHOT" if ref == "main" else "2.34.0-SNAPSHOT",
                )
                self.assertEqual(
                    config["RELEASE_BRANCH_NAME"],
                    "release/v3.0.x" if ref == "main" else "release/v2.33.x",
                )
                self.assertEqual(
                    config["VERSION"],
                    "3.0.0" if ref == "main" else "2.33.0",
                )

    def test_invalid_development_inputs(self):
        for ref, version in (
            ("release/2.x", "2.33.0-SNAPSHOT"),
            ("release/v2.33.x", "2.33.0-SNAPSHOT"),
            ("2.x", "3.0.0-SNAPSHOT"),
            ("2.x", "2.33.1-SNAPSHOT"),
            ("2.x", "2.33.0"),
            ("2.x", "2.33.0-alpha-SNAPSHOT"),
        ):
            with self.subTest(ref=ref, version=version):
                with self.assertRaises(ValueError):
                    release_config.development_config(ref, version)

    def test_minor_and_patch_release_routes(self):
        for version, prior in (("2.33.0", "2.32.0"), ("2.33.1", "2.33.0")):
            with self.subTest(version=version):
                self.assertEqual(
                    release_config.release_config(
                        "release/v2.33.x", version, "## Version 2.32.0 (2026-10-03)\n"
                    ),
                    {
                        "VERSION": version,
                        "DEVELOPMENT_BRANCH": "2.x",
                        "PRIOR_VERSION": prior,
                    },
                )
        self.assertEqual(
            release_config.release_config(
                "release/v3.1.x", "3.1.0", "## Version 3.0.0 (2026-10-03)\n"
            ),
            {
                "VERSION": "3.1.0",
                "DEVELOPMENT_BRANCH": "main",
                "PRIOR_VERSION": "3.0.0",
            },
        )

    def test_major_release_has_complete_previous_version(self):
        self.assertEqual(
            release_config.release_config(
                "release/v3.0.x",
                "3.0.0",
                "## Version 2.9.9 (2026-01-01)\n## Version 2.32.0 (2026-10-03)\n"
                "## Version 2.32.10 (2026-10-05)\n## Version 2.32.9 (2026-10-04)\n",
            )["PRIOR_VERSION"],
            "2.32.10",
        )

    def test_minor_release_uses_highest_previous_patch(self):
        self.assertEqual(
            release_config.release_config(
                "release/v2.33.x",
                "2.33.0",
                "## Version 2.33.0 (2026-10-06)\n## Version 2.32.0 (2026-10-03)\n"
                "## Version 2.32.10 (2026-10-05)\n## Version 2.32.9 (2026-10-04)\n",
            )["PRIOR_VERSION"],
            "2.32.10",
        )
        self.assertEqual(
            release_config.release_config(
                "release/v2.29.x", "2.29.0",
                (ROOT / "CHANGELOG.md").read_text(encoding="utf-8"),
            )["PRIOR_VERSION"],
            "2.28.1",
        )

    def test_invalid_release_inputs(self):
        for ref, version in (
            ("main", "3.0.0"),
            ("2.x", "2.33.0"),
            ("release/v2.32.x", "2.33.0"),
            ("release/v2.33.x", "2.33.0-SNAPSHOT"),
            ("release/v2.33.x", "2.33.0-alpha"),
        ):
            with self.subTest(ref=ref, version=version):
                with self.assertRaises(ValueError):
                    release_config.release_config(ref, version, "")
        for ref, version in (("release/v3.0.x", "3.0.0"), ("release/v2.33.x", "2.33.0")):
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                release_config.release_config(ref, version, "")

    def test_latest_release_policy(self):
        self.assertTrue(release_config.make_latest("2.33.0", ["v2.32.0"]))
        self.assertFalse(release_config.make_latest("2.33.0", ["v3.0.0", "v2.32.0"]))
        self.assertTrue(release_config.make_latest("3.0.0", ["v2.33.0"]))
        self.assertFalse(release_config.make_latest("3.0.1", ["v3.1.0"]))
        self.assertTrue(release_config.make_latest("2.33.0", ["v3.0.0-rc.1"]))
        self.assertTrue(release_config.make_latest("2.33.0", []))
        with self.assertRaises(ValueError):
            release_config.make_latest("2.33.0-SNAPSHOT", [])

    def test_post_release_baseline_is_monotonic(self):
        for version, baseline, update in (
            ("2.33.1", "2.34.0", False),
            ("2.34.0", "2.34.0", False),
            ("2.34.1", "2.34.0", True),
            ("2.9.10", "2.10.0", False),
            ("2.33.10", "2.33.9", True),
            ("3.0.1", "3.1.0", False),
            ("3.0.0", "2.32.0", True),
        ):
            with self.subTest(version=version, baseline=baseline):
                self.assertEqual(
                    release_config.post_release_config(version, baseline),
                    {"UPDATE_API_BASELINE": str(update).lower()},
                )
        for version, baseline in (
            ("2.33.0-SNAPSHOT", "2.32.0"),
            ("2.33.0", "2.32.0-SNAPSHOT"),
            ("2.33.0", "missing"),
        ):
            with self.subTest(version=version, baseline=baseline), self.assertRaises(ValueError):
                release_config.post_release_config(version, baseline)

    def test_post_release_cli_reads_baseline_not_snapshot(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            version_file = root / "version.gradle.kts"
            version_file.write_text(
                'val stableVersion = "2.35.0-SNAPSHOT"\n'
                'val apidiffBaselineVersion = "2.34.0"\n', encoding="utf-8"
            )
            output = io.StringIO()
            with (
                mock.patch.object(release_config, "REPO_ROOT", root),
                mock.patch.object(sys, "argv", ["release-config.py", "post-release", "--version", "2.33.1"]),
                contextlib.redirect_stdout(output),
            ):
                release_config.main()
            self.assertEqual(output.getvalue(), "UPDATE_API_BASELINE=false\n")
            version_file.write_text('val stableVersion = "2.35.0-SNAPSHOT"\n', encoding="utf-8")
            with (
                mock.patch.object(release_config, "REPO_ROOT", root),
                mock.patch.object(sys, "argv", ["release-config.py", "post-release", "--version", "2.33.1"]),
                contextlib.redirect_stderr(io.StringIO()),
                self.assertRaises(SystemExit) as error,
            ):
                release_config.main()
            self.assertEqual(error.exception.code, 2)

    def test_cli_reads_selected_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "version.gradle.kts").write_text(
                'val stableVersion = "2.33.0-SNAPSHOT"\n', encoding="utf-8"
            )
            output = io.StringIO()
            with (
                mock.patch.object(release_config, "REPO_ROOT", root),
                mock.patch.object(sys, "argv", ["release-config.py", "prepare", "--ref", "2.x"]),
                contextlib.redirect_stdout(output),
            ):
                release_config.main()
            self.assertEqual(
                output.getvalue(),
                "VERSION=2.33.0\nDEVELOPMENT_BRANCH=2.x\n"
                "RELEASE_BRANCH_NAME=release/v2.33.x\nNEXT_VERSION=2.34.0-SNAPSHOT\n",
            )


class WorkflowTest(unittest.TestCase):
    def test_preparation_prerequisites(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        for version, changelog, succeeds in (
            ("2.33.0-SNAPSHOT", "## Unreleased\n", True),
            ("3.0.0-SNAPSHOT", "## Unreleased\n", False),
            ("2.33.0-SNAPSHOT", "## Version 2.32.0 (2026-10-03)\n", False),
        ):
            with self.subTest(version=version, changelog=changelog), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                scripts = root / ".github" / "scripts"
                scripts.mkdir(parents=True)
                shutil.copyfile(
                    ROOT / ".github" / "scripts" / "release-config.py",
                    scripts / "release-config.py",
                )
                (root / "version.gradle.kts").write_text(
                    f'val stableVersion = "{version}"\n', encoding="utf-8"
                )
                (root / "CHANGELOG.md").write_text(changelog, encoding="utf-8")
                args = [
                    bash,
                    "-eo",
                    "pipefail",
                    "-c",
                    workflow_script("prepare-release-branch.yml", "Verify prerequisites"),
                ]
                if succeeds:
                    run_command(args, root, dict(os.environ, GITHUB_REF_NAME="2.x"))
                else:
                    with self.assertRaises(subprocess.CalledProcessError):
                        run_command(args, root, dict(os.environ, GITHUB_REF_NAME="2.x"))

    def test_preparation_pr_targets(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        for ref, version in (("main", "3.1.0-SNAPSHOT"), ("2.x", "2.33.0-SNAPSHOT")):
            for step, expected_base in (
                ("Create pull request against the release branch", "release"),
                ("Create pull request against the development branch", ref),
            ):
                with self.subTest(ref=ref, step=step), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    run_command(["git", "init", "--quiet", "--initial-branch", ref], root)
                    run_command(["git", "config", "user.name", "Release test"], root)
                    run_command(["git", "config", "user.email", "release-test@example.invalid"], root)
                    marker = root / "marker"
                    marker.write_text("before\n", encoding="utf-8")
                    run_command(["git", "add", "marker"], root)
                    run_command(["git", "commit", "--quiet", "-m", "fixture"], root)
                    marker.write_text("after\n", encoding="utf-8")
                    config = release_config.development_config(ref, version)
                    env = dict(os.environ, **config, CALLS="calls")
                    script = """
git() {
  if [[ $1 == push ]]; then
    printf '%s\\n' "$*" >> "$CALLS"
  else
    command git "$@"
  fi
}
gh() { printf '%s\\n' "$*" >> "$CALLS"; }
"""
                    script += workflow_script("prepare-release-branch.yml", step)
                    run_command([bash, "-eo", "pipefail", "-c", script], root, env)
                    calls = (root / "calls").read_text(encoding="utf-8")
                    base = config["RELEASE_BRANCH_NAME"] if expected_base == "release" else ref
                    self.assertIn(f"--base {base}", calls)
                    if ref == "2.x":
                        self.assertNotIn("--base main", calls)
                    self.assertEqual(
                        run_command(["git", "show", f"{ref}:marker"], root).stdout,
                        "before\n",
                    )

    def test_post_release_and_notes_targets(self):
        for filename in ("release.yml", "draft-release-notes.yml"):
            script = workflow_script(filename, "Create pull request against the development branch")
            self.assertIn('--base "$DEVELOPMENT_BRANCH"', script)
            self.assertNotIn("--base main", script)
        release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertEqual(release.count("ref: ${{ env.DEVELOPMENT_BRANCH }}"), 2)
        self.assertIn("DEVELOPMENT_BRANCH: ${{ needs.release.outputs.development-branch }}", release)
        self.assertIn('--latest="$MAKE_LATEST"', release)
        self.assertIn("group: publish-release\n      cancel-in-progress: false", release)
        self.assertLess(
            release.index("Determine whether to mark the release as latest"),
            release.index("Build and publish artifacts"),
        )
        contributors = (ROOT / ".github/scripts/generate-release-contributors.sh").read_text(encoding="utf-8")
        self.assertIn('git merge-base "origin/$development_branch" HEAD', contributors)
        self.assertEqual(contributors.count("base:$development_branch"), 2)

    def test_api_baseline_updates_selected_checkout(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        for version, prior in (("2.33.0", "2.32.0"), ("3.1.0", "3.0.0")):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                version_file = root / "version.gradle.kts"
                version_file.write_text(
                    f'val apidiffBaselineVersion = "{prior}"\n', encoding="utf-8"
                )
                gradlew = root / "gradlew"
                gradlew.write_text(
                    '#!/bin/sh\nprintf "%s\\n" "$*" >> gradle-calls\n',
                    encoding="utf-8",
                )
                gradlew.chmod(0o755)
                run_command(
                    [
                        bash,
                        "-eo",
                        "pipefail",
                        "-c",
                        workflow_script("release.yml", "Update apidiff baseline"),
                    ],
                    root,
                    dict(os.environ, VERSION=version, PRIOR_VERSION=prior),
                )
                self.assertEqual(
                    version_file.read_text(encoding="utf-8"),
                    f'val apidiffBaselineVersion = "{version}"\n',
                )
                self.assertEqual(
                    (root / "gradle-calls").read_text(encoding="utf-8"),
                    f"japicmp -PapiBaseVersion={prior} -PapiNewVersion={version}\n"
                    "--refresh-dependencies japicmp\n",
                )

    def test_post_release_workflow_gates_baseline_and_download_updates(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        for step in (
            "Wait for release to be available in maven central",
            "Update apidiff baseline",
        ):
            self.assertIn(
                f"- name: {step}\n        if: ${{{{ env.UPDATE_API_BASELINE == 'true' }}}}",
                release,
            )
        self.assertIn(
            "if: ${{ env.DEVELOPMENT_BRANCH == '2.x' && env.UPDATE_API_BASELINE == 'true' }}",
            release,
        )
        self.assertLess(
            release.index("ref: ${{ env.DEVELOPMENT_BRANCH }}", release.index("  post-release-updates:")),
            release.index("Determine whether to update the API baseline"),
        )
        self.assertLess(
            release.index("Determine whether to update the API baseline"),
            release.index("Wait for release to be available in maven central"),
        )
        self.assertIn(
            "- name: Merge change log to the development branch\n        env:", release,
        )
        for version, baseline, update in (
            ("2.33.1", "2.34.0", False),
            ("2.34.0", "2.34.0", False),
            ("2.34.1", "2.34.0", True),
        ):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                scripts = root / ".github/scripts"
                scripts.mkdir(parents=True)
                shutil.copyfile(ROOT / ".github/scripts/release-config.py", scripts / "release-config.py")
                version_file = root / "version.gradle.kts"
                version_file.write_text(f'val apidiffBaselineVersion = "{baseline}"\n', encoding="utf-8")
                readme = root / "README.md"
                readme.write_text(
                    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
                    f"releases/download/v{baseline}/opentelemetry-javaagent.jar\n",
                    encoding="utf-8",
                )
                original_readme = readme.read_text(encoding="utf-8")
                apidiffs = root / "docs/apidiffs"
                apidiffs.mkdir(parents=True)
                diff_file = apidiffs / "latest.txt"
                diff_file.write_text("existing API diff\n", encoding="utf-8")
                gradlew = root / "gradlew"
                gradlew.write_text(
                    '#!/bin/sh\nprintf "%s\\n" "$*" >> gradle-calls\n'
                    'echo updated > docs/apidiffs/latest.txt\n', encoding="utf-8"
                )
                gradlew.chmod(0o755)
                script = workflow_script("release.yml", "Determine whether to update the API baseline")
                script += '\nsource "$GITHUB_ENV"\nif [[ $UPDATE_API_BASELINE == true ]]; then\n'
                script += workflow_script("release.yml", "Update apidiff baseline") + "\n"
                script += workflow_script("release.yml", "Update development branch download link") + "\nfi\n"
                run_command(
                    [bash, "-eo", "pipefail", "-c", script], root,
                    dict(os.environ, VERSION=version, PRIOR_VERSION="2.34.0", GITHUB_ENV=str(root / "github-env")),
                )
                self.assertEqual(
                    version_file.read_text(encoding="utf-8"),
                    f'val apidiffBaselineVersion = "{version if update else baseline}"\n',
                )
                self.assertEqual((root / "gradle-calls").exists(), update)
                self.assertEqual(
                    readme.read_text(encoding="utf-8"),
                    original_readme.replace(f"v{baseline}/", f"v{version}/") if update else original_readme,
                )
                self.assertEqual(
                    diff_file.read_text(encoding="utf-8"),
                    "updated\n" if update else "existing API diff\n",
                )

    def test_download_links_follow_release_stream(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        for filename, step in (
            ("prepare-release-branch.yml", "Update download link version"),
            ("release.yml", "Update development branch download link"),
        ):
            for download in ("latest/download", "download/v2.32.0"):
                with self.subTest(filename=filename, download=download), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    readme = root / "README.md"
                    readme.write_text(
                        "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
                        f"releases/{download}/opentelemetry-javaagent.jar\n",
                        encoding="utf-8",
                    )
                    run_command(
                        [bash, "-eo", "pipefail", "-c", workflow_script(filename, step)],
                        root,
                        dict(os.environ, VERSION="2.33.0"),
                    )
                    self.assertEqual(
                        readme.read_text(encoding="utf-8"),
                        "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
                        "releases/download/v2.33.0/opentelemetry-javaagent.jar\n",
                    )
        release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("if: ${{ env.DEVELOPMENT_BRANCH == '2.x' && env.UPDATE_API_BASELINE == 'true' }}", release)

    def test_renovate_follow_up_hooks_remain_branch_local(self):
        for filename in ("auto-license-report.yml", "auto-update-gh-aw-lockfiles.yml"):
            text = (ROOT / ".github/workflows" / filename).read_text(encoding="utf-8")
            self.assertIn("'renovate/**", text)
            self.assertNotIn("ref: main", text)
            self.assertNotIn("schedule:", text)
            self.assertIn("git push", text)
        license_workflow = (ROOT / ".github/workflows/auto-license-report.yml").read_text(encoding="utf-8")
        self.assertRegex(
            license_workflow,
            r"uses: actions/download-artifact@[^\n]+\n        with:\n"
            r"          name: patch\n          path: \$\{\{ runner.temp \}\}",
        )

    def test_license_patch_absence_skips_apply(self):
        bash = shutil.which("bash")
        self.assertIsNotNone(bash, "Git Bash or bash is required")
        for content in ("", "license changes\n"):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                output = root / "output"
                output.write_text("", encoding="utf-8")
                script = """
git() {
  if [[ $1 == diff ]]; then
    printf '%s' "$PATCH_CONTENT"
  fi
}
"""
                script += workflow_script("auto-license-report.yml", "Create patch file")
                run_command(
                    [bash, "-eo", "pipefail", "-c", script],
                    root,
                    dict(os.environ, GITHUB_OUTPUT=str(output), PATCH_CONTENT=content),
                )
                self.assertEqual(
                    output.read_text(encoding="utf-8"),
                    "exists=true\n" if content else "",
                )
        workflow = (ROOT / ".github/workflows/auto-license-report.yml").read_text(encoding="utf-8")
        self.assertIn("patch-exists: ${{ steps.create-patch.outputs.exists }}", workflow)
        self.assertRegex(
            workflow,
            r"  apply:\n    runs-on: ubuntu-latest\n    needs: generate\n"
            r"    if: needs\.generate\.outputs\.patch-exists == 'true'\n",
        )


if __name__ == "__main__":
    unittest.main()
