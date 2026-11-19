import tempfile
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

from smoke_test_image_adoption import (
    BOT_EMAIL,
    BOT_NAME,
    FAMILIES,
    PIN_FILES,
    PR_BODY,
    REPOSITORY,
    TITLE,
    AdoptionError,
    image_pin,
    is_newer_run,
    latest_successful_run,
    missing_publication_comments,
    no_window_flags,
    publication_id,
    publication_id_matches,
    publication_comment,
    post_publication_comments,
    prepare_branch,
    reconcile,
    remote_head,
    require_expected_remote_head,
    run_process,
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


def initialize_remote(root, remote):
    root.mkdir()
    run_process(["git", "init", "--bare", str(remote)])
    run_process(["git", "init", "-b", "main"], cwd=root)
    run_process(["git", "config", "user.name", "test"], cwd=root)
    run_process(["git", "config", "user.email", "test@example.com"], cwd=root)
    for path, content in pin_files().items():
        destination = root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
    run_process(["git", "add", *PIN_FILES], cwd=root)
    run_process(["git", "commit", "-m", "initial pins"], cwd=root)
    run_process(["git", "remote", "add", "origin", str(remote)], cwd=root)
    run_process(["git", "push", "--set-upstream", "origin", "main"], cwd=root)
    return run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()


def create_adoption_branch(root, family, version):
    run_process(["git", "checkout", "-b", "adoption"], cwd=root)
    run_process(["git", "config", "user.name", BOT_NAME], cwd=root)
    run_process(["git", "config", "user.email", BOT_EMAIL], cwd=root)
    updated, _ = update_pin(pin_files(), family, version)
    for path, content in updated.items():
        (root / path).write_text(content, encoding="utf-8")
    run_process(["git", "add", *PIN_FILES], cwd=root)
    run_process(["git", "commit", "-m", TITLE], cwd=root)
    run_process(
        ["git", "push", "origin", "HEAD:refs/heads/otelbot/smoke-test-image-versions"],
        cwd=root,
    )


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

    def test_concurrent_remote_update_is_rejected_by_non_force_push(self):
        from smoke_test_image_adoption import publish_update

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            other = Path(directory) / "other-checkout"
            initialize_remote(root, remote)
            create_adoption_branch(root, "grpc", "20261120.987654000001")
            run_process(
                [
                    "git",
                    "clone",
                    "--branch",
                    "otelbot/smoke-test-image-versions",
                    str(remote),
                    str(other),
                ]
            )
            run_process(["git", "config", "user.name", BOT_NAME], cwd=other)
            run_process(["git", "config", "user.email", BOT_EMAIL], cwd=other)

            expected_head = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
            branch_files = {
                path: run_process(["git", "show", f"HEAD:{path}"], cwd=root)
                for path in PIN_FILES
            }
            branch_files, _ = update_pin(
                branch_files, "fake-backend", "20261120.123456000001"
            )
            concurrent_files, _ = update_pin(
                branch_files, "play", "20261120.987655000001"
            )
            for path, content in concurrent_files.items():
                (other / path).write_text(content, encoding="utf-8")
            run_process(["git", "add", *PIN_FILES], cwd=other)
            run_process(["git", "commit", "-m", TITLE], cwd=other)
            concurrent_head = run_process(
                ["git", "rev-parse", "HEAD"], cwd=other
            ).strip()

            real_run_process = run_process

            def race_before_push(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    real_run_process(
                        [
                            "git",
                            "push",
                            "origin",
                            "HEAD:refs/heads/otelbot/smoke-test-image-versions",
                        ],
                        cwd=other,
                    )
                    return ""
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch("smoke_test_image_adoption.run_process", side_effect=race_before_push),
            ):
                with self.assertRaisesRegex(AdoptionError, "non-force push refused"):
                    publish_update(
                        root,
                        type("FakeGitHub", (), {"token": None})(),
                        branch_files,
                        {"number": 42},
                        expected_head,
                        [],
                    )

            self.assertEqual(
                run_process(
                    ["git", "ls-remote", "--heads", "origin", "otelbot/smoke-test-image-versions"],
                    cwd=root,
                ).split()[0],
                concurrent_head,
            )
    def test_read_only_reconciliation_dry_run_does_not_push(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"

            initialize_remote(root, remote)

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

    def test_dry_run_then_apply_creates_branch_in_same_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            main_sha = initialize_remote(root, remote)
            run = run_record(sha=main_sha)
            tag = publication_id(run)
            run["created_at"] = "2026-11-19T12:00:00Z"

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return main_sha

                def open_adoption_prs(self):
                    return []

                def workflow_runs(self, family):
                    return [run] if family == "fake-backend" else []

                def run_attempt(self, _run_id, _attempt):
                    return run

                def compare(self, _base, _head):
                    return "identical"

                def create_pr(self, body):
                    self.created_body = body
                    return {"number": 123}

                def pr_comments(self, _number):
                    return self.comments

                def create_pr_comment(self, _number, body):
                    self.comments.append({"body": body, "user": {"login": BOT_NAME}})

                comments = []

            api = FakeGitHub()
            real_run_process = run_process

            def run_process_with_auth(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(
                        ["git", "config", "user.name", BOT_NAME], cwd=cwd
                    )
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    return ""
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch(
                    "smoke_test_image_adoption.verify_eligible_run",
                    return_value=(
                        run,
                        tag,
                        f"https://github.com/{REPOSITORY}/actions/runs/"
                        f"{run['id']}/attempts/{run['run_attempt']}",
                    ),
                ),
                patch(
                    "smoke_test_image_adoption.resolve_pinned_run",
                    return_value=run_record(run_id=100, sha=main_sha),
                ),
                patch("smoke_test_image_adoption.run_process", side_effect=run_process_with_auth),
            ):
                dry_message, has_updates = reconcile(api, root, None, False)
                self.assertIn("Dry run", dry_message)
                self.assertTrue(has_updates)
                self.assertEqual(
                    run_process(
                        [
                            "git",
                            "ls-remote",
                            "--heads",
                            "origin",
                            "otelbot/smoke-test-image-versions",
                        ],
                        cwd=root,
                    ),
                    "",
                )

                apply_message, applied = reconcile(api, root, None, True)

            self.assertIn("Opened draft smoke-test adoption PR #123", apply_message)
            self.assertTrue(applied)
            self.assertEqual(api.created_body, PR_BODY)
            self.assertEqual(len(api.comments), 1)
            self.assertTrue(
                run_process(
                    [
                        "git",
                        "ls-remote",
                        "--heads",
                        "origin",
                        "otelbot/smoke-test-image-versions",
                    ],
                    cwd=root,
                )
            )

    def test_main_advancement_merges_and_preserves_pending_family_pins(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            initial_sha = initialize_remote(root, remote)
            grpc_tag = "20261120.987654000001"
            create_adoption_branch(root, "grpc", grpc_tag)

            run_process(["git", "checkout", "main"], cwd=root)
            main_files = pin_files()
            main_files, _ = update_pin(main_files, "play", "20261120.111111000001")
            for path, content in main_files.items():
                (root / path).write_text(content, encoding="utf-8")
            (root / "main-advancement.txt").write_text("unrelated main change\n", encoding="utf-8")
            run_process(["git", "add", *PIN_FILES, "main-advancement.txt"], cwd=root)
            run_process(["git", "commit", "-m", "advance main"], cwd=root)
            run_process(["git", "push", "origin", "main"], cwd=root)
            current_main = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()

            class FakeGitHub:
                def open_adoption_prs(self):
                    return [{"number": 42, "base": {"ref": "main"}}]

            base_ref, base_files, branch_files, _pr, expected_head = prepare_branch(
                root, FakeGitHub()
            )
            self.assertNotEqual(initial_sha, current_main)
            self.assertEqual(image_pin(base_files, "play"), "20261120.111111000001")
            self.assertEqual(image_pin(branch_files, "play"), "20261120.111111000001")
            self.assertEqual(image_pin(branch_files, "grpc"), grpc_tag)
            self.assertEqual(expected_head, remote_head(root, "otelbot/smoke-test-image-versions"))
            self.assertEqual(
                run_process(["git", "merge-base", "HEAD", base_ref], cwd=root).strip(),
                current_main,
            )
            self.assertEqual(
                run_process(["git", "show", "HEAD:main-advancement.txt"], cwd=root),
                "unrelated main change\n",
            )

            run_process(
                ["git", "push", "origin", "HEAD:refs/heads/otelbot/smoke-test-image-versions"],
                cwd=root,
            )
            _, second_base, second_branch, _, _ = prepare_branch(root, FakeGitHub())
            self.assertEqual(image_pin(second_base, "play"), image_pin(second_branch, "play"))
            self.assertEqual(image_pin(second_branch, "grpc"), grpc_tag)

    def test_conflicting_main_pin_change_is_aborted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            initialize_remote(root, remote)
            create_adoption_branch(root, "fake-backend", "20261120.987654000001")
            run_process(["git", "checkout", "main"], cwd=root)
            main_files, _ = update_pin(pin_files(), "fake-backend", "20261120.111111000001")
            for path, content in main_files.items():
                (root / path).write_text(content, encoding="utf-8")
            run_process(["git", "add", *PIN_FILES], cwd=root)
            run_process(["git", "commit", "-m", "advance fake-backend pin"], cwd=root)
            run_process(["git", "push", "origin", "main"], cwd=root)

            class FakeGitHub:
                def open_adoption_prs(self):
                    return [{"number": 42, "base": {"ref": "main"}}]

            with self.assertRaisesRegex(AdoptionError, "conflicts with pending adoption pins"):
                prepare_branch(root, FakeGitHub())
            self.assertEqual(run_process(["git", "status", "--porcelain"], cwd=root), "")

    def test_older_completion_does_not_replace_a_pending_pin(self):
        current = pin_files()
        newer = run_record(run_id=123457, sha="a" * 40)
        newer["created_at"] = "2026-11-20T12:00:00Z"
        newer_tag = publication_id(newer)
        branch, _ = update_pin(current, "fake-backend", newer_tag)
        older = run_record(run_id=123456, sha="a" * 40)
        older["created_at"] = "2026-11-19T12:00:00Z"
        older_tag = publication_id(older)
        main_sha = "a" * 40

        class FakeGitHub:
            def main_sha(self):
                return main_sha

            def workflow_runs(self, family):
                return [older] if family == "fake-backend" else []

            def run_attempt(self, _run_id, _attempt):
                return older

            def compare(self, _base, _head):
                return "identical"

            def pr_comments(self, _number):
                update = (
                    "fake-backend",
                    image_pin(current, "fake-backend"),
                    newer_tag,
                    str(newer["id"]),
                    newer["run_attempt"],
                    f"https://github.com/{REPOSITORY}/actions/runs/"
                    f"{newer['id']}/attempts/{newer['run_attempt']}",
                )
                return [{"body": publication_comment(update), "user": {"login": BOT_NAME}}]

        pr = {"number": 42, "base": {"ref": "main"}}
        head = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, current, branch, pr, head),
            ),
            patch(
                "smoke_test_image_adoption.verify_eligible_run",
                return_value=(
                    older,
                    older_tag,
                    f"https://github.com/{REPOSITORY}/actions/runs/"
                    f"{older['id']}/attempts/{older['run_attempt']}",
                ),
            ),
            patch(
                "smoke_test_image_adoption.resolve_pinned_run",
                return_value=newer,
            ),
        ):
            message, has_updates = reconcile(FakeGitHub(), ROOT, None, False)

        self.assertIn("No newer verified image versions found.", message)
        self.assertFalse(has_updates)

    def test_publication_comments_are_append_only_and_idempotent(self):
        original_body = "Human context before update."
        pr = {"number": 123, "body": original_body}
        update = (
            "fake-backend",
            "20260825.1",
            TAG,
            "123456",
            1,
            "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
            "actions/runs/123456/attempts/1",
        )

        class FakeGitHub:
            comments = [
                {"body": "Existing human comment", "user": {"login": "human"}}
            ]

            def pr_comments(self, _number):
                return list(self.comments)

            def create_pr_comment(self, _number, body):
                pr["body"] = "Human edit at final write boundary."
                self.comments.append({"body": body, "user": {"login": BOT_NAME}})

        api = FakeGitHub()
        post_publication_comments(api, pr, [update])
        post_publication_comments(api, pr, [update])

        self.assertEqual(pr["body"], "Human edit at final write boundary.")
        self.assertEqual(api.comments[0]["body"], "Existing human comment")
        self.assertEqual(len(api.comments), 2)
        self.assertIn(TAG, api.comments[1]["body"])
        self.assertIn(f"/actions/runs/123456", api.comments[1]["body"])
        self.assertEqual(missing_publication_comments(api.comments, [update]), [])

    def test_non_bot_comment_cannot_claim_publication_identity(self):
        update = (
            "fake-backend",
            "20260825.1",
            TAG,
            "123456",
            1,
            "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
            "actions/runs/123456/attempts/1",
        )
        with self.assertRaisesRegex(AdoptionError, "non-bot PR comment"):
            missing_publication_comments(
                [{"body": publication_comment(update), "user": {"login": "human"}}],
                [update],
            )

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
