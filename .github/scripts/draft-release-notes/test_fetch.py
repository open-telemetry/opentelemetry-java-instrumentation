import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("fetch.py")
SPEC = importlib.util.spec_from_file_location("draft_release_notes_fetch", MODULE_PATH)
fetch = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = fetch
SPEC.loader.exec_module(fetch)


class UserFacingSourceTest(unittest.TestCase):
    def test_detects_runtime_source(self):
        self.assertTrue(
            fetch.touches_user_facing_src_main(
                ["instrumentation/example/src/main/java/Example.java"]
            )
        )

    def test_ignores_testing_source(self):
        self.assertFalse(
            fetch.touches_user_facing_src_main(
                ["instrumentation/example/testing/src/main/java/TestHelper.java"]
            )
        )


class ReferenceTest(unittest.TestCase):
    def test_extracts_issue_and_pr_references(self):
        self.assertEqual(
            fetch.extract_reference_numbers(
                [
                    "Refs #123 and #456.",
                    f"[issue #789](https://github.com/{fetch.REPO}/issues/789)",
                    f"https://github.com/{fetch.REPO}/pull/456",
                ],
                {123},
            ),
            [456, 789],
        )

    def test_ignores_non_issue_link_labels(self):
        self.assertEqual(
            fetch.extract_reference_numbers(
                [
                    f"[run #27828265164](https://github.com/{fetch.REPO}/actions/runs/27828265164)",
                    "[issue #123](https://github.com/other/repo/issues/123)",
                    "Also see #456.",
                ],
                set(),
            ),
            [456],
        )

    def test_missing_reference_is_retained_without_retry(self):
        error = subprocess.CalledProcessError(
            1,
            ["gh", "issue", "view", "19974"],
            stderr=(
                "GraphQL: Could not resolve to an issue or pull request "
                "with the number of 19974. (repository.issue)"
            ),
        )
        with (
            mock.patch.object(fetch, "load_json", side_effect=error) as load_json,
            mock.patch.object(fetch.time, "sleep") as sleep,
            mock.patch.object(fetch, "warn") as warn,
        ):
            result = fetch.fetch_ref_data(19974)

        self.assertEqual(result["number"], 19974)
        self.assertTrue(result["unavailable"])
        load_json.assert_called_once()
        sleep.assert_not_called()
        warn.assert_called_once()

    def test_reference_api_failure_is_not_hidden(self):
        for message in (
            "GraphQL: Resource not accessible by integration (repository)",
            "HTTP 502: Bad Gateway",
            "GraphQL: Could not resolve to a Repository with the name 'other/repo'.",
        ):
            with self.subTest(message=message):
                error = subprocess.CalledProcessError(1, ["gh"], stderr=message)
                with (
                    mock.patch.object(fetch, "load_json", side_effect=error),
                    mock.patch.object(fetch.time, "sleep"),
                    mock.patch.object(fetch, "warn"),
                ):
                    with self.assertRaises(subprocess.CalledProcessError):
                        fetch.fetch_ref_data(19974)

    def test_missing_candidate_pr_is_still_fatal(self):
        error = subprocess.CalledProcessError(
            1,
            ["gh"],
            stderr="Could not resolve to an issue or pull request with the number of 123.",
        )
        with (
            mock.patch.object(fetch, "load_json", side_effect=error),
            mock.patch.object(fetch.time, "sleep"),
            mock.patch.object(fetch, "warn"),
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                fetch.fetch_pr_data(123)

    def test_bundle_is_generated_with_unavailable_reference(self):
        candidate = fetch.Candidate(
            commit_hash="abc123",
            subject="Runtime change (#19903)",
            pr_number=19903,
            files=["instrumentation/example/src/main/java/Example.java"],
            touches_src_main=True,
            deprecated_added=False,
            deprecated_removed=False,
        )
        error = subprocess.CalledProcessError(
            1,
            ["gh"],
            stderr="Could not resolve to an issue or pull request with the number of 19974.",
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle_dir = root / "build" / "changelog-bundle"
            with (
                mock.patch.object(fetch, "REPO_ROOT", root),
                mock.patch.object(
                    fetch,
                    "fetch_pr_data",
                    return_value={
                        "body": (
                            "Stacked on #19974. "
                            f"[run #27828265164](https://github.com/{fetch.REPO}/actions/runs/27828265164)"
                        )
                    },
                ),
                mock.patch.object(fetch, "load_json", side_effect=error),
                mock.patch.object(fetch, "get_patch_from_git", return_value="+runtime change\n"),
                mock.patch.object(fetch.time, "sleep"),
                mock.patch.object(fetch, "warn"),
            ):
                fetch.prepare_bundle(bundle_dir, "2.0.0", "v1.0.0..HEAD", [candidate], [], [])

            manifest = json.loads((bundle_dir / "manifest.json").read_text(encoding="utf-8"))
            reference = json.loads(
                (bundle_dir / "refs" / "19974" / "meta.json").read_text(encoding="utf-8")
            )
            self.assertEqual(manifest["candidates"][0]["issue_refs"], [19974])
            self.assertTrue(reference["unavailable"])
            self.assertTrue((bundle_dir / "index.md").exists())


class CommandTest(unittest.TestCase):
    def test_windows_commands_do_not_create_console_windows(self):
        with (
            mock.patch.object(fetch.sys, "platform", "win32"),
            mock.patch.object(fetch.subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True),
            mock.patch.object(fetch.subprocess, "run") as run,
        ):
            fetch.run_command(["gh", "--version"])

        self.assertEqual(run.call_args.kwargs["creationflags"], 0x08000000)

    def test_non_windows_commands_use_default_creation_flags(self):
        with (
            mock.patch.object(fetch.sys, "platform", "linux"),
            mock.patch.object(fetch.subprocess, "run") as run,
        ):
            fetch.run_command(["gh", "--version"])

        self.assertEqual(run.call_args.kwargs["creationflags"], 0)


if __name__ == "__main__":
    unittest.main()
