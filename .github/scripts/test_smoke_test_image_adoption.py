import tempfile
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

from smoke_test_image_adoption import (
    BODY_END,
    BODY_START,
    FAMILIES,
    PIN_FILES,
    REPOSITORY,
    AdoptionError,
    generated_body,
    image_pin,
    is_newer_run,
    latest_successful_run,
    no_window_flags,
    publication_id,
    publication_id_matches,
    reconcile,
    refresh_pr_body,
    replace_body_section,
    require_expected_remote_head,
    update_pin,
    validate_bot_branch,
    validate_candidate,
    verify_required_jobs,
    expected_references,
)


ROOT = Path(__file__).resolve().parents[2]
TAG = "20261119.123456000001"


def pin_files():
    return {
        path: (ROOT / path).read_text(encoding="utf-8")
        for path in PIN_FILES
    }


def run_record(family="fake-backend", run_id=123456, attempt=1, sha="a" * 40):
    family_config = FAMILIES[family]
    return {
        "id": run_id,
        "run_attempt": attempt,
        "name": family_config.workflow_name,
        "path": f".github/workflows/{family_config.workflow_file}",
        "head_branch": "main",
        "event": "push",
        "status": "completed",
        "conclusion": "success",
        "head_sha": sha,
        "created_at": "2026-11-19T12:00:00Z",
    }


def candidate_record(run=None, family="fake-backend", references=None):
    run = run or run_record(family)
    tag = publication_id(run)
    references = references or expected_references(family, tag)
    return {
        "schema_version": 1,
        "family": family,
        "repository": REPOSITORY,
        "source_commit": run["head_sha"],
        "workflow": (
            f"{REPOSITORY}/.github/workflows/"
            f"{FAMILIES[family].workflow_file}@refs/heads/main"
        ),
        "run_id": str(run["id"]),
        "run_attempt": str(run["run_attempt"]),
        "publication_id": tag,
        "images": [
            {"reference": reference, "digest": "sha256:" + "a" * 64}
            for reference in sorted(references)
        ],
    }


class AdoptionTest(unittest.TestCase):
    def test_publication_id_uses_exact_run_attempt(self):
        run = run_record(run_id=123456, attempt=2)
        self.assertEqual(publication_id(run), "20261119.123456000002")
        run["run_started_at"] = "2026-11-20T01:00:00Z"
        self.assertEqual(publication_id(run), "20261120.123456000002")

    def test_publication_id_allows_the_tag_step_to_cross_utc_midnight(self):
        run = run_record(run_id=123456, attempt=2)
        run["run_started_at"] = "2026-11-19T23:59:30Z"
        run["updated_at"] = "2026-11-20T00:02:00Z"
        self.assertTrue(
            publication_id_matches(run, "20261120.123456000002")
        )
        self.assertFalse(
            publication_id_matches(run, "20261121.123456000002")
        )
        self.assertFalse(
            publication_id_matches(run, "20261120.123456000003")
        )

    def test_candidate_requires_complete_family_and_exact_provenance(self):
        run = run_record()
        expected = expected_references("fake-backend", publication_id(run))
        candidate = candidate_record(run)
        self.assertEqual(
            set(validate_candidate(candidate, run, "fake-backend", 123456, 1, expected)),
            expected,
        )
        for mutate in (
            lambda value: value["images"].pop(),
            lambda value: value.update(run_id="654321"),
            lambda value: value.update(repository="attacker/repository"),
            lambda value: value["images"][0].update(digest="not-a-digest"),
            lambda value: value.update(workflow="attacker/path"),
        ):
            invalid = candidate_record(run)
            mutate(invalid)
            with self.subTest(candidate=invalid), self.assertRaises(AdoptionError):
                validate_candidate(invalid, run, "fake-backend", 123456, 1, expected)

    def test_complete_candidates_for_each_family(self):
        for family in FAMILIES:
            with self.subTest(family=family):
                run = run_record(family)
                if family == "servlet":
                    expected = {
                        f"ghcr.io/{REPOSITORY}/smoke-test-servlet-wildfly:"
                        f"13.0.0.Final-jdk8-{publication_id(run)}"
                    }
                else:
                    expected = expected_references(family, publication_id(run))
                self.assertEqual(
                    set(
                        validate_candidate(
                            candidate_record(run, family, expected),
                            run,
                            family,
                            run["id"],
                            run["run_attempt"],
                            expected,
                        )
                    ),
                    expected,
                )

    def test_each_family_updates_only_its_pin(self):
        initial = pin_files()
        for family in FAMILIES:
            with self.subTest(family=family):
                updated, old = update_pin(initial, family, TAG)
                self.assertNotEqual(old, TAG)
                for path in PIN_FILES:
                    if path != FAMILIES[family].pin_file:
                        self.assertEqual(updated[path], initial[path])
                    self.assertEqual(
                        updated[path].count(f'"{TAG}"'),
                        1 if path == FAMILIES[family].pin_file else 0,
                    )
        windows = initial[FAMILIES["fake-backend"].pin_file]
        updated, _ = update_pin(initial, "fake-backend", TAG)
        self.assertIn(
            "FAKE_BACKEND_WINDOWS_VERSION = FAKE_BACKEND_VERSION;",
            updated[FAMILIES["fake-backend"].pin_file],
        )
        self.assertIn(
            "FAKE_BACKEND_WINDOWS_VERSION = FAKE_BACKEND_VERSION;",
            windows,
        )

    def test_repeated_delivery_is_idempotent(self):
        updated, _ = update_pin(pin_files(), "fake-backend", TAG)
        again, old = update_pin(updated, "fake-backend", TAG)
        self.assertEqual(old, TAG)
        self.assertEqual(again, updated)

    def test_multiple_pending_families_are_preserved(self):
        base = pin_files()
        after_fake, _ = update_pin(base, "fake-backend", TAG)
        after_grpc, _ = update_pin(after_fake, "grpc", "20261120.987654000001")
        self.assertEqual(image_pin(after_grpc, "fake-backend"), TAG)
        self.assertEqual(image_pin(after_grpc, "grpc"), "20261120.987654000001")
        self.assertEqual(
            after_grpc[FAMILIES["fake-backend"].pin_file],
            after_fake[FAMILIES["fake-backend"].pin_file],
        )

    def test_out_of_order_runs_use_source_lineage(self):
        older = run_record(run_id=10, sha="a" * 40)
        newer = run_record(run_id=11, sha="b" * 40)

        def compare(base, head):
            return "ahead" if base == older["head_sha"] and head == newer["head_sha"] else "behind"

        self.assertTrue(is_newer_run(older, newer, compare))
        self.assertFalse(is_newer_run(newer, older, compare))
        with self.assertRaises(AdoptionError):
            is_newer_run(older, newer, lambda _base, _head: "diverged")

    def test_latest_run_selection_uses_source_lineage_not_newest_delivery(self):
        older = run_record(run_id=20, sha="a" * 40)
        older["created_at"] = "2026-11-19T13:00:00Z"
        newer = run_record(run_id=19, sha="b" * 40)
        newer["created_at"] = "2026-11-19T12:00:00Z"

        class FakeGitHub:
            def workflow_runs(self, _family):
                return [older, newer]

            def compare(self, base, head):
                if base == older["head_sha"] and head == newer["head_sha"]:
                    return "ahead"
                if base == newer["head_sha"] and head == "c" * 40:
                    return "ahead"
                return "behind"

        self.assertEqual(
            latest_successful_run(FakeGitHub(), "fake-backend", "c" * 40),
            newer,
        )

    def test_required_jobs_fail_closed_for_partial_or_cancelled_runs(self):
        verify_required_jobs(
            [
                {"name": "prepare", "status": "completed", "conclusion": "success"},
                {"name": "publishLinux", "status": "completed", "conclusion": "success"},
                {"name": "publishWindows", "status": "completed", "conclusion": "success"},
                {"name": "verify", "status": "completed", "conclusion": "success"},
            ],
            "fake-backend",
        )
        with self.assertRaises(AdoptionError):
            verify_required_jobs(
                [
                    {"name": "prepare", "status": "completed", "conclusion": "success"},
                    {"name": "publishLinux", "status": "completed", "conclusion": "success"},
                    {"name": "verify", "status": "completed", "conclusion": "cancelled"},
                ],
                "fake-backend",
            )

    def test_api_pagination_accepts_rest_array_responses(self):
        from smoke_test_image_adoption import GitHub

        api = GitHub.__new__(GitHub)
        with patch.object(api, "request", return_value=[{"number": 123}]):
            self.assertEqual(api.pages("pulls?state=open"), [{"number": 123}])

    def test_manual_validation_verifies_the_requested_publication(self):
        current = pin_files()
        run = run_record(run_id=123456, attempt=2)
        main_sha = "c" * 40

        class FakeGitHub:
            def main_sha(self):
                return main_sha

            def run_attempt(self, run_id, attempt):
                self.assert_requested = (run_id, attempt)
                return run

            def workflow_runs(self, _family):
                raise AssertionError("manual validation must not select another run")

            def compare(self, _base, _head):
                raise AssertionError("the mocked ordering check should be used")

        api = FakeGitHub()
        verified = (run, TAG, "https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/runs/123456")
        previous = run_record(run_id=100, sha="b" * 40)
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, current, current, None, None),
            ),
            patch(
                "smoke_test_image_adoption.verify_eligible_run",
                return_value=verified,
            ) as verify,
            patch(
                "smoke_test_image_adoption.resolve_pinned_run",
                return_value=previous,
            ),
            patch("smoke_test_image_adoption.is_newer_run", return_value=True),
        ):
            message, has_updates = reconcile(
                api, ROOT, (run["id"], run["run_attempt"], None), False
            )

        verify.assert_called_once_with(api, run, "fake-backend", main_sha, ROOT)
        self.assertEqual(api.assert_requested, (run["id"], run["run_attempt"]))
        self.assertTrue(has_updates)
        self.assertIn(f"{image_pin(current, 'fake-backend')} -> {TAG}", message)
        self.assertIn(verified[2], message)

    def test_unexpected_branch_edits_or_authors_are_rejected(self):
        base = pin_files()
        updated, _ = update_pin(base, "fake-backend", TAG)
        commits = [
            (
                "otelbot",
                "197425009+otelbot@users.noreply.github.com",
                "otelbot",
                "197425009+otelbot@users.noreply.github.com",
                "Update smoke-test image versions",
            )
        ]
        self.assertEqual(
            validate_bot_branch(base, updated, commits),
            {"fake-backend": TAG},
        )
        human_edit = dict(updated)
        human_edit[PIN_FILES[0]] += "\n// human edit\n"
        with self.assertRaises(AdoptionError):
            validate_bot_branch(base, human_edit, commits)
        with self.assertRaises(AdoptionError):
            validate_bot_branch(
                base,
                updated,
                [("human", "human@example.com", "human", "human@example.com", "edit pins")],
            )

    def test_compare_and_swap_race_is_refused(self):
        require_expected_remote_head("abc123", "abc123")
        with self.assertRaises(AdoptionError):
            require_expected_remote_head("abc123", "def456")

    def test_read_only_reconciliation_dry_run_does_not_push(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            root.mkdir()
            for path, content in pin_files().items():
                destination = root / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(content, encoding="utf-8")
            from smoke_test_image_adoption import run_process

            run_process(["git", "init", "--bare", str(remote)])
            run_process(["git", "init", "-b", "main"], cwd=root)
            run_process(["git", "config", "user.name", "test"], cwd=root)
            run_process(["git", "config", "user.email", "test@example.com"], cwd=root)
            run_process(["git", "add", *PIN_FILES], cwd=root)
            run_process(["git", "commit", "-m", "initial pins"], cwd=root)
            run_process(["git", "remote", "add", "origin", str(remote)], cwd=root)
            run_process(["git", "push", "--set-upstream", "origin", "main"], cwd=root)

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()

                def open_adoption_prs(self):
                    return []

                def workflow_runs(self, _family):
                    return []

            message, has_updates = reconcile(FakeGitHub(), root, None, False)
            self.assertIn("Dry run; no branch or PR writes performed.", message)
            self.assertFalse(has_updates)
            self.assertEqual(
                run_process(
                    ["git", "ls-remote", "--heads", "origin", "otelbot/smoke-test-image-versions"],
                    cwd=root,
                ),
                "",
            )

    def test_pr_summary_replacement_preserves_human_context(self):
        original = "Human context before.\n\n" + generated_body(
            {"fake-backend": ("20260825.1", TAG, "123456")}
        ) + "\n\nHuman context after."
        replacement = generated_body(
            {
                "fake-backend": ("20260825.1", TAG, "123456"),
                "grpc": ("20260825.2", "20261120.987654000001", "987654"),
            }
        )
        updated = replace_body_section(original, replacement)
        self.assertTrue(updated.startswith("Human context before."))
        self.assertTrue(updated.endswith("Human context after."))
        self.assertIn(BODY_START, updated)
        self.assertIn(BODY_END, updated)

    def test_pr_body_refresh_preserves_or_refuses_concurrent_human_edits(self):
        base = pin_files()
        branch, _ = update_pin(base, "fake-backend", TAG)
        original_body = (
            "Human context before.\n\n"
            + generated_body({"fake-backend": ("20260825.1", "20261119.1", "1")})
            + "\n\nHuman context after."
        )
        pr = {"number": 123, "body": original_body}
        family_links = {
            "fake-backend": (
                "https://github.com/open-telemetry/"
                "opentelemetry-java-instrumentation/actions/runs/123456"
            )
        }

        class FakeGitHub:
            body = original_body

            def get_pr(self, _number):
                return {"number": 123, "body": self.body}

        api = FakeGitHub()
        refreshed = refresh_pr_body(api, pr, base, branch, family_links)
        self.assertTrue(refreshed.startswith("Human context before."))
        self.assertTrue(refreshed.endswith("Human context after."))

        api.body = original_body.replace("Human context after.", "Updated by a human.")
        with self.assertRaisesRegex(AdoptionError, "body changed during update"):
            refresh_pr_body(api, pr, base, branch, family_links)

    def test_python_subprocesses_do_not_open_windows(self):
        with patch.object(sys, "platform", "win32"), patch.object(
            subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True
        ):
            self.assertEqual(no_window_flags(), 0x08000000)

    @patch("smoke_test_image_adoption.subprocess.run")
    def test_process_helper_passes_windows_no_window_flag(self, run):
        from smoke_test_image_adoption import run_process

        run.return_value.returncode = 0
        run.return_value.stdout = "done"
        with patch.object(sys, "platform", "win32"), patch.object(
            subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True
        ):
            run_process(["git", "status"])
        self.assertEqual(run.call_args.kwargs["creationflags"], 0x08000000)


if __name__ == "__main__":
    unittest.main()
