import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/reusable-update-otel-sdk.yml").read_text(
    encoding="utf-8"
)


def workflow_script(step):
    match = re.search(
        rf"^      (?:- |  )name: {re.escape(step)}\n.*?^        run: \|\n"
        r"((?:          [^\n]*\n|\n)+)",
        WORKFLOW,
        re.MULTILINE | re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"missing workflow step: {step}")
    return "\n".join(line[10:] for line in match.group(1).splitlines())


class UpdateSdkWorkflowTest(unittest.TestCase):
    def check_versions(self, target, current, open_pr_base="", open_pr_version="1.66.0"):
        with tempfile.TemporaryDirectory() as directory:
            cwd = Path(directory)
            (cwd / "dependencyManagement").mkdir()
            (cwd / "dependencyManagement/build.gradle.kts").write_text(
                f'val otelSdkVersion = "{current}"\n', encoding="utf-8"
            )
            output = cwd / "output"
            calls = cwd / "calls"
            mock_gh = """
gh() {
  if [[ "$1 $2" == "release view" ]]; then
    echo v1.66.0
  elif [[ "$1 $2" == "pr list" ]]; then
    printf '%s\\n' "$@" > "$CALLS"
    local base search
    while (( $# )); do
      case "$1" in
        --base) base=$2; shift ;;
        --search) search=$2; shift ;;
      esac
      shift
    done
    if [[ "$base" == "$OPEN_PR_BASE" &&
          "$search" == "in:title \\"Update the OpenTelemetry SDK version to $OPEN_PR_VERSION\\"" ]]; then
      echo "123 Update the OpenTelemetry SDK version to $OPEN_PR_VERSION"
    fi
  else
    return 1
  fi
}
"""
            subprocess.run(
                [
                    "bash", "-e", "-o", "pipefail", "-c",
                    mock_gh + workflow_script("Check versions"),
                ],
                cwd=cwd,
                env={
                    **os.environ,
                    "TARGET_BRANCH": target,
                    "GITHUB_OUTPUT": str(output),
                    "CALLS": str(calls),
                    "OPEN_PR_BASE": open_pr_base,
                    "OPEN_PR_VERSION": open_pr_version,
                },
                capture_output=True,
                text=True,
                check=True,
            )
            args = calls.read_text(encoding="utf-8").splitlines()
            self.assertEqual(args[args.index("--base") + 1], target)
            self.assertEqual(args[args.index("--state") + 1], "open")
            # Existing PRs using the old bot branch name must also be detected.
            self.assertNotIn("--head", args)
            return dict(
                line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines()
            )

    def should_update(self, outputs):
        condition = re.search(
            r"^    if: \|\n((?:      [^\n]*\n)+)", WORKFLOW, re.MULTILINE
        ).group(1).strip()
        for key, value in outputs.items():
            condition = condition.replace(f"needs.check-versions.outputs.{key}", f"'{value}'")
        return subprocess.run(
            ["bash", "-c", f"[[ {condition} ]]"], capture_output=True, check=False
        ).returncode == 0

    def test_independent_versions_and_valid_distinct_branches(self):
        branches = set()
        for target, current in (("main", "1.65.0"), ("maintenance/v2.x", "1.64.0")):
            with self.subTest(target=target):
                outputs = self.check_versions(target, current)
                self.assertEqual(outputs["current-version"], current)
                self.assertEqual(outputs["latest-version"], "1.66.0")
                self.assertEqual(outputs["already-opened"], "false")
                self.assertTrue(self.should_update(outputs))
                branch = outputs["branch"]
                self.assertIn(target.replace("/", "-"), branch)
                subprocess.run(
                    ["git", "check-ref-format", "--branch", branch],
                    check=True,
                    capture_output=True,
                )
                branches.add(branch)
        self.assertEqual(len(branches), 2)

    def test_up_to_date_target_is_skipped(self):
        for target in ("main", "maintenance/v2.x"):
            with self.subTest(target=target):
                self.assertFalse(self.should_update(self.check_versions(target, "1.66.0")))

    def test_duplicate_detection_is_target_and_version_specific(self):
        for target, other in (("main", "maintenance/v2.x"), ("maintenance/v2.x", "main")):
            with self.subTest(target=target):
                duplicate = self.check_versions(target, "1.65.0", open_pr_base=target)
                self.assertEqual(duplicate["already-opened"], "true")
                self.assertFalse(self.should_update(duplicate))
                self.assertTrue(
                    self.should_update(self.check_versions(target, "1.65.0", open_pr_base=other))
                )
                self.assertTrue(
                    self.should_update(
                        self.check_versions(
                            target, "1.65.0", open_pr_base=target, open_pr_version="1.65.0"
                        )
                    )
                )

    def test_pull_request_uses_target_and_checked_branch(self):
        for target in ("main", "maintenance/v2.x"):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                outputs = self.check_versions(target, "1.65.0")
                result = subprocess.run(
                    ["bash", "-e", "-o", "pipefail", "-c", """
gh() { printf 'gh'; printf ' <%s>' "$@"; printf '\\n'; }
git() { printf 'git'; printf ' <%s>' "$@"; printf '\\n'; }
""" + workflow_script("Create pull request")],
                    cwd=directory,
                    env={
                        **os.environ,
                        "TARGET_BRANCH": target,
                        "BRANCH": outputs["branch"],
                        "VERSION": outputs["latest-version"],
                    },
                    capture_output=True,
                    text=True,
                    check=True,
                )
                commands = result.stdout
                self.assertIn(f"git <checkout> <-b> <{outputs['branch']}>", commands)
                self.assertIn(f"git <push> <--set-upstream> <origin> <{outputs['branch']}>", commands)
                self.assertIn(f"<--base> <{target}>", commands)


if __name__ == "__main__":
    unittest.main()
