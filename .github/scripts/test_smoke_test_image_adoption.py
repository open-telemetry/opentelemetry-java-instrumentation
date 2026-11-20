import io
import tempfile
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

from smoke_test_image_adoption import (
    APP_LOGIN,
    BOT_EMAIL,
    BOT_NAME,
    CandidateArtifactUnavailable,
    FAMILIES,
    GitHistory,
    PIN_FILES,
    PR_BODY,
    REPOSITORY,
    TITLE,
    AdoptionError,
    NotFound,
    expected_references,
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
    resolve_pinned_run,
    require_expected_remote_head,
    run_process,
    update_pin,
    validate_bot_branch,
    validate_candidate,
    validate_current_main,
    validate_run_identity,
    verify_eligible_run,
    verify_required_jobs,
)
from smoke_test_image_refresh import REFRESH_FAMILIES, selected_families


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


def adoption_pr(number, head_sha, state="open", merged=False):
    return {
        "number": number,
        "title": "Update smoke-test image versions",
        "state": state,
        "merged": merged,
        "merged_at": "2026-11-20T12:00:00Z" if merged else None,
        "base": {"ref": "main", "repo": {"full_name": REPOSITORY}},
        "head": {
            "ref": "otelbot/smoke-test-image-versions",
            "sha": head_sha,
            "repo": {"full_name": REPOSITORY},
        },
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
        "repository": {"full_name": REPOSITORY},
        "head_repository": {"full_name": REPOSITORY},
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
    def test_scheduled_and_manual_refresh_selection_is_allowlisted(self):
        self.assertEqual(selected_families("schedule", None), list(REFRESH_FAMILIES))
        self.assertEqual(
            selected_families("workflow_dispatch", "all"), list(REFRESH_FAMILIES)
        )
        for family in REFRESH_FAMILIES:
            with self.subTest(family=family):
                self.assertEqual(selected_families("workflow_dispatch", family), [family])
                self.assertEqual(
                    FAMILIES[family].workflow_file,
                    f"publish-smoke-test-{family}-images.yml",
                )
        self.assertNotIn("servlet", REFRESH_FAMILIES)
        self.assertNotIn("early-jdk8", REFRESH_FAMILIES)

    def test_refresh_dispatch_preserves_source_triggers_and_adoption_provenance(self):
        workflows = ROOT / ".github" / "workflows"
        adoption_workflow = (workflows / "propose-smoke-test-image-updates.yml").read_text()
        refresh_workflow = (workflows / "refresh-smoke-test-image-bases.yml").read_text()
        self.assertIn("actions: write", refresh_workflow)
        self.assertIn("--field ref=main", refresh_workflow)
        for family in REFRESH_FAMILIES:
            with self.subTest(family=family):
                publisher = workflows / FAMILIES[family].workflow_file
                contents = publisher.read_text()
                self.assertIn("  push:\n", contents)
                self.assertIn(f'"smoke-tests/images/{family}/**"', contents)
                self.assertIn("      - main\n", contents)
                self.assertIn("  workflow_dispatch:\n", contents)
                self.assertIn("cancel-in-progress: false", contents)
                self.assertIn(
                    f"      - {FAMILIES[family].workflow_name}\n", adoption_workflow
                )
                if family == "quarkus":
                    self.assertIn("skip-java-8: true", contents)
                    self.assertIn("skip-java-11: true", contents)
                    self.assertIn("skip-java-17: true", contents)

                run = run_record(family)
                run["event"] = "workflow_dispatch"
                tag = validate_run_identity(run, family, run["id"], run["run_attempt"])
                expected = expected_references(family, tag)
                candidate_images = validate_candidate(
                    candidate_record(run, family, expected),
                    run,
                    family,
                    run["id"],
                    run["run_attempt"],
                    expected,
                )
                self.assertEqual(set(candidate_images), expected)

    def test_refresh_selection_rejects_untrusted_inputs(self):
        for event_name, family in (
            ("schedule", "grpc"),
            ("workflow_dispatch", "servlet"),
            ("workflow_dispatch", "early-jdk8"),
            ("workflow_dispatch", "arbitrary-workflow"),
            ("push", "all"),
        ):
            with (
                self.subTest(event_name=event_name, family=family),
                self.assertRaises(ValueError),
            ):
                selected_families(event_name, family)

    def test_fake_backend_windows_build_pulls_its_digest_pinned_base(self):
        image_build = (ROOT / "smoke-tests/images/fake-backend/build.gradle.kts").read_text()
        dockerfile = (
            ROOT / "smoke-tests/images/fake-backend/src/docker/backend/windows.dockerfile"
        ).read_text()
        self.assertIn('pull.set(true)', image_build)
        self.assertIn("@sha256:", dockerfile.splitlines()[0])

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

    def test_latest_run_selection_uses_git_history_without_per_source_api_comparisons(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            main_sha = initialize_remote(root, remote)
            source_shas = [main_sha]
            for index in range(40):
                (root / "history.txt").write_text(f"{index}\n", encoding="utf-8")
                run_process(["git", "add", "history.txt"], cwd=root)
                run_process(["git", "commit", "-m", f"history {index}"], cwd=root)
                source_shas.append(
                    run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
                )
            main_sha = source_shas[-1]
            runs = [
                run_record(run_id=2000 + index, sha=sha)
                for index, sha in enumerate(source_shas)
            ]
            for index, run in enumerate(runs):
                run["created_at"] = f"2026-11-20T12:{59 - index:02}:00Z"

            class FakeGitHub:
                compare_calls = 0

                def workflow_runs(self, _family):
                    return runs

                def compare(self, _base, _head):
                    self.compare_calls += 1
                    raise AssertionError("historical comparisons must use local Git history")

            api = FakeGitHub()
            latest = latest_successful_run(
                api, "fake-backend", main_sha, GitHistory(root).compare
            )

        self.assertEqual(latest["head_sha"], main_sha)
        self.assertEqual(api.compare_calls, 0)

    def test_legacy_pin_resolves_the_successful_rerun_attempt(self):
        run = run_record(run_id=123456, attempt=2)
        requested = []

        class FakeGitHub:
            def workflow_run(self, run_id):
                self.assert_run_id = run_id
                return run

            def run_attempt(self, run_id, attempt):
                requested.append((run_id, attempt))
                return run

        self.assertEqual(
            resolve_pinned_run(FakeGitHub(), "fake-backend", "20261119.123456"),
            run,
        )
        self.assertEqual(requested, [("123456", "2")])

    def test_legacy_pin_resolves_an_earlier_success_after_a_failed_rerun(self):
        latest = run_record(run_id=123456, attempt=2)
        latest["conclusion"] = "failure"
        earlier = run_record(run_id=123456, attempt=1)
        requested = []

        class FakeGitHub:
            def workflow_run(self, _run_id):
                return latest

            def run_attempt(self, run_id, attempt):
                requested.append((run_id, attempt))
                if attempt == "2":
                    return latest
                return earlier

        self.assertEqual(
            resolve_pinned_run(FakeGitHub(), "fake-backend", "20261119.123456"),
            earlier,
        )
        self.assertEqual(requested, [("123456", "2"), ("123456", "1")])

    def test_modern_pin_does_not_query_a_concatenated_legacy_run_id(self):
        run = run_record(run_id=123456, attempt=2)
        requested = []

        class FakeGitHub:
            def run_attempt(self, run_id, attempt):
                requested.append((run_id, attempt))
                return run

            def workflow_run(self, _run_id):
                raise AssertionError("a verified modern pin must not query legacy provenance")

        self.assertEqual(
            resolve_pinned_run(FakeGitHub(), "fake-backend", publication_id(run)),
            run,
        )
        self.assertEqual(requested, [("123456", 2)])

    def test_long_legacy_run_id_is_not_confused_with_a_modern_attempt(self):
        run = run_record(run_id=123456789012, attempt=2)
        modern_lookalike = run_record(
            family="play", run_id=123456, attempt=789012
        )
        requested = []

        class FakeGitHub:
            def workflow_run(self, run_id):
                self.assert_run_id = run_id
                return run

            def run_attempt(self, run_id, attempt):
                requested.append((run_id, attempt))
                return modern_lookalike if run_id == "123456789" else run

        self.assertEqual(
            resolve_pinned_run(
                FakeGitHub(), "fake-backend", "20261119.123456789012"
            ),
            run,
        )
        self.assertEqual(
            requested,
            [("123456", 789012), ("123456789012", "2")],
        )

    def test_legacy_run_id_ending_in_six_zeroes_resolves_as_legacy_pin(self):
        run = run_record(run_id=37550000000)
        requested = []

        class FakeGitHub:
            def workflow_run(self, run_id):
                self.assert_run_id = run_id
                return run

            def run_attempt(self, run_id, attempt):
                requested.append((run_id, attempt))
                return run

        api = FakeGitHub()
        self.assertEqual(
            resolve_pinned_run(api, "fake-backend", "20261119.37550000000"),
            run,
        )
        self.assertEqual(api.assert_run_id, "37550000000")
        self.assertEqual(requested, [("37550000000", "1")])

    def test_legacy_pin_requires_a_matching_verified_successful_attempt(self):
        run = run_record(run_id=123456, attempt=2)

        class FakeGitHub:
            def workflow_run(self, _run_id):
                return run

            def run_attempt(self, run_id, attempt):
                invalid = dict(run)
                invalid["conclusion"] = "failure"
                return invalid

        with self.assertRaises(AdoptionError):
            resolve_pinned_run(FakeGitHub(), "fake-backend", "20261119.123456")

        class SuccessfulAttempt:
            def workflow_run(self, _run_id):
                return run

            def run_attempt(self, _run_id, _attempt):
                return run

        with self.assertRaises(AdoptionError):
            resolve_pinned_run(
                SuccessfulAttempt(), "fake-backend", "20261120.123456"
            )

        class MissingRun:
            def workflow_run(self, _run_id):
                raise NotFound("missing")

        with self.assertRaises(AdoptionError):
            resolve_pinned_run(MissingRun(), "fake-backend", "20261119.123456")

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

    def test_verify_eligible_run_checks_publication_artifact_and_registry(self):
        main_sha = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        run = run_record(sha=main_sha)
        run["event"] = "workflow_dispatch"
        expected = expected_references("fake-backend", publication_id(run))
        digest = "sha256:" + "a" * 64

        class FakeGitHub:
            def __init__(self):
                self.exact_run = dict(run)
                self.artifacts = [
                    {
                        "id": 456,
                        "name": (
                            "smoke-test-publication-fake-backend-"
                            f"{run['run_attempt']}"
                        ),
                        "expired": False,
                        "workflow_run": {"id": run["id"], "head_sha": main_sha},
                    }
                ]
                self.candidate = candidate_record(run)

            def run_attempt(self, run_id, attempt):
                self.assert_requested = (run_id, attempt)
                return self.exact_run

            def compare(self, _source, _main):
                return "identical"

            def run_jobs(self, _run_id, _attempt):
                return [
                    {"name": name, "status": "completed", "conclusion": "success"}
                    for name in ("prepare", "publishLinux", "publishWindows", "verify")
                ]

            def run_artifacts(self, _run_id):
                return self.artifacts

            def download_artifact(self, artifact_id):
                self.downloaded_artifact = artifact_id
                return self.candidate

        def verify(api):
            return verify_eligible_run(api, run, "fake-backend", main_sha, ROOT)

        api = FakeGitHub()
        with patch("smoke_test_image_adoption.manifest_digest", return_value=digest) as manifest:
            exact_run, tag, run_url = verify(api)

        self.assertEqual(exact_run, run)
        self.assertEqual(tag, publication_id(run))
        self.assertEqual(api.assert_requested, (run["id"], run["run_attempt"]))
        self.assertEqual(api.downloaded_artifact, 456)
        self.assertEqual(
            {call.args[0] for call in manifest.call_args_list},
            expected,
        )
        self.assertIn(
            f"/actions/runs/{run['id']}/attempts/{run['run_attempt']}",
            run_url,
        )

        expired_api = FakeGitHub()
        expired_api.artifacts[0]["expired"] = True
        with patch(
            "smoke_test_image_adoption.manifest_digest",
            return_value=digest,
        ):
            with self.assertRaises(CandidateArtifactUnavailable):
                verify(expired_api)

        with patch(
            "smoke_test_image_adoption.manifest_digest",
            return_value="sha256:" + "b" * 64,
        ):
            with self.assertRaisesRegex(AdoptionError, "registry manifest changed"):
                verify(FakeGitHub())

        invalid_cases = (
            (
                "expired artifact",
                lambda api: api.artifacts[0].update(expired=True),
                "no unique unexpired candidate artifact",
            ),
            (
                "wrong artifact run",
                lambda api: api.artifacts[0]["workflow_run"].update(id=654321),
                "no unique unexpired candidate artifact",
            ),
            (
                "wrong artifact source",
                lambda api: api.artifacts[0]["workflow_run"].update(head_sha="b" * 40),
                "no unique unexpired candidate artifact",
            ),
            (
                "wrong candidate run",
                lambda api: api.candidate.update(run_id="654321"),
                "candidate provenance does not match",
            ),
            (
                "wrong candidate attempt",
                lambda api: api.candidate.update(run_attempt="2"),
                "candidate provenance does not match",
            ),
            (
                "wrong candidate source",
                lambda api: api.candidate.update(source_commit="b" * 40),
                "candidate provenance does not match",
            ),
            (
                "wrong exact attempt",
                lambda api: api.exact_run.update(run_attempt=2),
                "publisher run identity",
            ),
            (
                "wrong source repository",
                lambda api: api.exact_run.update(
                    head_repository={
                        "full_name": "attacker/opentelemetry-java-instrumentation"
                    }
                ),
                "publisher run identity",
            ),
            (
                "duplicate artifacts",
                lambda api: api.artifacts.append(dict(api.artifacts[0])),
                "no unique unexpired candidate artifact",
            ),
        )
        for name, mutate, message in invalid_cases:
            with self.subTest(case=name):
                api = FakeGitHub()
                mutate(api)
                with patch(
                    "smoke_test_image_adoption.manifest_digest",
                    return_value=digest,
                ):
                    with self.assertRaisesRegex(AdoptionError, message):
                        verify(api)

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

    def test_concurrent_remote_update_is_rejected_by_exact_push_lease(self):
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
            main_sha = run_process(["git", "rev-parse", "main"], cwd=root).strip()
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
                    return ""
                if args[:2] == ["git", "push"]:
                    real_run_process(
                        [
                            "git",
                            "push",
                            "origin",
                            "HEAD:refs/heads/otelbot/smoke-test-image-versions",
                        ],
                        cwd=other,
                    )
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return main_sha

                def pull_request(self, _number):
                    return adoption_pr(42, expected_head)

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch("smoke_test_image_adoption.run_process", side_effect=race_before_push),
            ):
                with self.assertRaisesRegex(AdoptionError, "changed during publication"):
                    publish_update(
                        root,
                        FakeGitHub(),
                        branch_files,
                        adoption_pr(42, expected_head),
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

    def test_deleted_adoption_branch_is_not_recreated_by_racing_publication(self):
        from smoke_test_image_adoption import publish_update

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            initialize_remote(root, remote)
            create_adoption_branch(root, "grpc", "20261120.987654000001")
            expected_head = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
            main_sha = run_process(["git", "rev-parse", "main"], cwd=root).strip()
            branch_files = {
                path: run_process(["git", "show", f"HEAD:{path}"], cwd=root)
                for path in PIN_FILES
            }
            branch_files, _ = update_pin(
                branch_files, "fake-backend", "20261120.123456000001"
            )
            real_run_process = run_process

            def delete_before_push(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    return ""
                if args[:2] == ["git", "push"]:
                    real_run_process(
                        [
                            "git",
                            "push",
                            "origin",
                            "--delete",
                            "otelbot/smoke-test-image-versions",
                        ],
                        cwd=cwd,
                    )
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return main_sha

                def pull_request(self, _number):
                    return adoption_pr(42, expected_head)

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch("smoke_test_image_adoption.run_process", side_effect=delete_before_push),
            ):
                with self.assertRaisesRegex(AdoptionError, "changed during publication"):
                    publish_update(
                        root,
                        FakeGitHub(),
                        branch_files,
                        adoption_pr(42, expected_head),
                        expected_head,
                        [],
                    )

            self.assertIsNone(remote_head(root, "otelbot/smoke-test-image-versions"))

    def test_concurrent_adoption_branch_rewind_is_rejected_by_exact_push_lease(self):
        from smoke_test_image_adoption import publish_update

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            initialize_remote(root, remote)
            create_adoption_branch(root, "grpc", "20261120.987654000001")
            expected_head = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
            main_sha = run_process(["git", "rev-parse", "main"], cwd=root).strip()
            branch_files = {
                path: run_process(["git", "show", f"HEAD:{path}"], cwd=root)
                for path in PIN_FILES
            }
            branch_files, _ = update_pin(
                branch_files, "fake-backend", "20261120.123456000001"
            )
            real_run_process = run_process

            def rewind_before_push(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    return ""
                if args[:2] == ["git", "push"]:
                    real_run_process(
                        [
                            "git",
                            "push",
                            "--force",
                            "origin",
                            "main:refs/heads/otelbot/smoke-test-image-versions",
                        ],
                        cwd=cwd,
                    )
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return main_sha

                def pull_request(self, _number):
                    return adoption_pr(42, expected_head)

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch("smoke_test_image_adoption.run_process", side_effect=rewind_before_push),
            ):
                with self.assertRaisesRegex(AdoptionError, "changed during publication"):
                    publish_update(
                        root,
                        FakeGitHub(),
                        branch_files,
                        adoption_pr(42, expected_head),
                        expected_head,
                        [],
                    )

            self.assertEqual(
                remote_head(root, "otelbot/smoke-test-image-versions"),
                main_sha,
            )

    def test_new_adoption_branch_refuses_a_branch_created_during_publication(self):
        from smoke_test_image_adoption import publish_update

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            main_sha = initialize_remote(root, remote)
            branch_files, _ = update_pin(
                pin_files(), "fake-backend", "20261120.123456000001"
            )
            real_run_process = run_process

            def create_branch_before_push(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    return ""
                if args[:2] == ["git", "push"]:
                    real_run_process(
                        [
                            "git",
                            "push",
                            "origin",
                            f"{main_sha}:refs/heads/otelbot/smoke-test-image-versions",
                        ],
                        cwd=cwd,
                    )
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            class FakeGitHub:
                token = None

                def main_sha(self):
                    return main_sha

                def open_adoption_prs(self):
                    return []

                def create_pr(self, _body):
                    raise AssertionError("a concurrently created branch must not open a PR")

            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch(
                    "smoke_test_image_adoption.run_process",
                    side_effect=create_branch_before_push,
                ),
            ):
                with self.assertRaisesRegex(AdoptionError, "changed during publication"):
                    publish_update(root, FakeGitHub(), branch_files, None, None, [("update",)])

            self.assertEqual(remote_head(root, "otelbot/smoke-test-image-versions"), main_sha)

    def test_closed_or_merged_adoption_pr_is_refused_before_publication(self):
        from smoke_test_image_adoption import publish_update

        for state, merged in (("closed", False), ("closed", True)):
            with self.subTest(state=state, merged=merged), tempfile.TemporaryDirectory() as directory:
                root = Path(directory) / "checkout"
                remote = Path(directory) / "remote.git"
                initialize_remote(root, remote)
                create_adoption_branch(root, "grpc", "20261120.987654000001")
                expected_head = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
                main_sha = run_process(["git", "rev-parse", "main"], cwd=root).strip()
                branch_files = {
                    path: run_process(["git", "show", f"HEAD:{path}"], cwd=root)
                    for path in PIN_FILES
                }
                branch_files, _ = update_pin(
                    branch_files, "fake-backend", "20261120.123456000001"
                )

                class FakeGitHub:
                    token = None

                    def main_sha(self):
                        return main_sha

                    def pull_request(self, _number):
                        return adoption_pr(42, expected_head, state=state, merged=merged)

                real_run_process = run_process

                def run_process_with_auth(args, cwd=None, env=None, input_text=None):
                    if args == [".github/scripts/use-cla-approved-bot.sh"]:
                        real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                        return real_run_process(
                            ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                        )
                    if args[:3] == ["gh", "auth", "setup-git"]:
                        return ""
                    return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

                with (
                    patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                    patch(
                        "smoke_test_image_adoption.run_process",
                        side_effect=run_process_with_auth,
                    ),
                ):
                    with self.assertRaisesRegex(AdoptionError, "closed or no longer targets"):
                        publish_update(
                            root,
                            FakeGitHub(),
                            branch_files,
                            adoption_pr(42, expected_head),
                            expected_head,
                            [],
                        )

                self.assertEqual(
                    remote_head(root, "otelbot/smoke-test-image-versions"),
                    expected_head,
                )

    def test_pr_closed_during_push_is_not_reported_or_commented(self):
        from smoke_test_image_adoption import publish_update

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            remote = Path(directory) / "remote.git"
            initialize_remote(root, remote)
            create_adoption_branch(root, "grpc", "20261120.987654000001")
            expected_head = run_process(["git", "rev-parse", "HEAD"], cwd=root).strip()
            main_sha = run_process(["git", "rev-parse", "main"], cwd=root).strip()
            branch_files = {
                path: run_process(["git", "show", f"HEAD:{path}"], cwd=root)
                for path in PIN_FILES
            }
            branch_files, _ = update_pin(
                branch_files, "fake-backend", "20261120.123456000001"
            )

            class FakeGitHub:
                token = None
                comments = []
                state = "open"

                def main_sha(self):
                    return main_sha

                def pull_request(self, _number):
                    return adoption_pr(42, expected_head, state=self.state)

                def pr_comments(self, _number):
                    return self.comments

                def create_pr_comment(self, _number, body):
                    self.comments.append({"body": body})

            api = FakeGitHub()
            real_run_process = run_process

            def close_during_push(args, cwd=None, env=None, input_text=None):
                if args == [".github/scripts/use-cla-approved-bot.sh"]:
                    real_run_process(["git", "config", "user.name", BOT_NAME], cwd=cwd)
                    return real_run_process(
                        ["git", "config", "user.email", BOT_EMAIL], cwd=cwd
                    )
                if args[:3] == ["gh", "auth", "setup-git"]:
                    return ""
                if args[:2] == ["git", "push"]:
                    api.state = "closed"
                return real_run_process(args, cwd=cwd, env=env, input_text=input_text)

            update = (
                "fake-backend",
                "20260825.1",
                "20261120.123456000001",
                "123456",
                1,
                "https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
                "actions/runs/123456/attempts/1",
            )
            with (
                patch.dict("os.environ", {"GH_TOKEN": "test-token"}),
                patch("smoke_test_image_adoption.run_process", side_effect=close_during_push),
            ):
                with self.assertRaisesRegex(AdoptionError, "closed or no longer targets"):
                    publish_update(
                        root,
                        api,
                        branch_files,
                        adoption_pr(42, expected_head),
                        expected_head,
                        [update],
                    )

            self.assertEqual(api.comments, [])
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
                    self.created_pr = adoption_pr(
                        123,
                        remote_head(root, "otelbot/smoke-test-image-versions"),
                    )
                    return {"number": 123}

                def pull_request(self, _number):
                    return self.created_pr

                def pr_comments(self, _number):
                    return self.comments

                def create_pr_comment(self, _number, body):
                    self.comments.append(
                        {"body": body, "user": {"login": APP_LOGIN, "type": "Bot"}}
                    )

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

    def test_main_snapshot_for_verification_is_the_fetched_main_commit(self):
        stale_main_sha = "a" * 40
        prepared_main_sha = "b" * 40
        run = run_record("grpc", sha=prepared_main_sha)
        current = pin_files()
        candidate = candidate_record(run, "grpc")
        required_images = expected_references("grpc", publication_id(run))
        image_name = next(iter(required_images)).split(":", 1)[0]
        required_images.add(f"{image_name}:jdk29-{publication_id(run)}")

        class FakeGitHub:
            main_sha_calls = 0

            def main_sha(self):
                self.main_sha_calls += 1
                return stale_main_sha

            def run_attempt(self, _run_id, _attempt):
                return run

            def compare(self, _source, _main):
                return "identical"

            def run_jobs(self, _run_id, _attempt):
                return [
                    {"name": name, "status": "completed", "conclusion": "success"}
                    for name in ("publish / build", "publish / verify")
                ]

            def run_artifacts(self, _run_id):
                return [
                    {
                        "id": 456,
                        "name": (
                            "smoke-test-publication-grpc-"
                            f"{run['run_attempt']}"
                        ),
                        "expired": False,
                        "workflow_run": {
                            "id": run["id"],
                            "head_sha": prepared_main_sha,
                        },
                    }
                ]

            def download_artifact(self, _artifact_id):
                return candidate

        api = FakeGitHub()
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(prepared_main_sha, current, current, None, None),
            ),
            patch(
                "smoke_test_image_adoption.expected_images_for",
                return_value=required_images,
            ) as expected_images,
            self.assertRaisesRegex(
                AdoptionError, "complete expected image family"
            ),
        ):
            reconcile(
                api,
                ROOT,
                (run["id"], run["run_attempt"], run),
                False,
            )

        expected_images.assert_called_once_with(
            "grpc", publication_id(run), ROOT, prepared_main_sha
        )
        self.assertEqual(api.main_sha_calls, 0)

    def test_publication_requires_main_to_match_the_verified_snapshot(self):
        live_main_sha = "b" * 40

        class FakeGitHub:
            def main_sha(self):
                return live_main_sha

        with self.assertRaisesRegex(AdoptionError, "main changed during adoption"):
            validate_current_main(
                ROOT, FakeGitHub(), live_main_sha, expected_main_sha="a" * 40
            )

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
                return [
                    {
                        "body": publication_comment(update),
                        "user": {"login": APP_LOGIN, "type": "Bot"},
                    }
                ]

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
                self.comments.append(
                    {"body": body, "user": {"login": APP_LOGIN, "type": "Bot"}}
                )

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
        for author in (
            {"login": "human", "type": "User"},
            {"login": "another-app[bot]", "type": "Bot"},
            {"login": APP_LOGIN, "type": "User"},
        ):
            with self.subTest(author=author), self.assertRaisesRegex(
                AdoptionError, "non-bot PR comment"
            ):
                missing_publication_comments(
                    [{"body": publication_comment(update), "user": author}],
                    [update],
                )

    def test_selected_cross_midnight_pin_skips_expired_artifact_and_other_family_updates(self):
        current = pin_files()
        selected_version = "20261120.123456000001"
        current, _ = update_pin(current, "fake-backend", selected_version)
        main_sha = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        selected = run_record(run_id=123456, sha=main_sha)
        selected["run_started_at"] = "2026-11-19T23:59:30Z"
        selected["completed_at"] = "2026-11-20T00:02:00Z"
        next_family = run_record("play", run_id=234567, sha=main_sha)
        verified_families = []

        class FakeGitHub:
            def main_sha(self):
                return main_sha

            def workflow_runs(self, family):
                if family == "fake-backend":
                    return [selected]
                if family == "play":
                    return [next_family]
                return []

            def run_attempt(self, run_id, _attempt):
                return {str(selected["id"]): selected, str(next_family["id"]): next_family}[
                    str(run_id)
                ]

            def compare(self, _base, _head):
                return "ahead"

        def verify(_api, run, family, _main_sha, _root):
            verified_families.append(family)
            if family == "fake-backend":
                raise AssertionError("an already selected pin must not need its artifact")
            return (
                run,
                publication_id(run),
                f"https://github.com/{REPOSITORY}/actions/runs/{run['id']}",
            )

        old_run = run_record(run_id=100, sha="a" * 40)
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, current, current, None, None),
            ),
            patch("smoke_test_image_adoption.verify_eligible_run", side_effect=verify),
            patch("smoke_test_image_adoption.resolve_pinned_run", return_value=old_run),
        ):
            message, has_updates = reconcile(FakeGitHub(), ROOT, None, False)

        self.assertEqual(verified_families, ["play"])
        self.assertTrue(has_updates)
        self.assertIn("play:", message)

    def test_older_successful_run_with_unavailable_artifact_does_not_block_updates(self):
        main_sha = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        selected_fake_backend = run_record(run_id=123456, sha=main_sha)
        expired_fake_backend = run_record(run_id=123455, sha=main_sha)
        selected_play = run_record("play", run_id=200, sha=main_sha)
        newer_play = run_record("play", run_id=234567, sha=main_sha)
        base = pin_files()
        current, _ = update_pin(
            base, "fake-backend", publication_id(selected_fake_backend)
        )
        current, _ = update_pin(current, "play", publication_id(selected_play))
        play_candidate = candidate_record(newer_play, "play")
        play_artifact = {
            "id": 789,
            "name": f"smoke-test-publication-play-{newer_play['run_attempt']}",
            "expired": False,
            "workflow_run": {
                "id": newer_play["id"],
                "head_sha": main_sha,
            },
        }

        class FakeGitHub:
            def __init__(self):
                self.runs = {
                    ("fake-backend", expired_fake_backend["id"]): expired_fake_backend,
                    ("fake-backend", selected_fake_backend["id"]): selected_fake_backend,
                    ("play", selected_play["id"]): selected_play,
                    ("play", newer_play["id"]): newer_play,
                }
                self.artifact_requests = []

            def main_sha(self):
                return main_sha

            def workflow_runs(self, family):
                if family == "fake-backend":
                    return [expired_fake_backend]
                if family == "play":
                    return [newer_play]
                return []

            def run_attempt(self, run_id, _attempt):
                for run in self.runs.values():
                    if str(run["id"]) == str(run_id):
                        return run
                raise NotFound("run not found")

            def compare(self, _base, _head):
                return "identical"

            def run_jobs(self, run_id, _attempt):
                return [
                    {"name": name, "status": "completed", "conclusion": "success"}
                    for name in ("publish / build", "publish / verify")
                ]

            def run_artifacts(self, run_id):
                self.artifact_requests.append(str(run_id))
                if str(run_id) == str(expired_fake_backend["id"]):
                    return []
                return [play_artifact]

            def download_artifact(self, _artifact_id):
                return play_candidate

            def pr_comments(self, _number):
                return []

        api = FakeGitHub()
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, base, current, None, None),
            ),
            patch(
                "smoke_test_image_adoption.manifest_digest",
                return_value="sha256:" + "a" * 64,
            ),
        ):
            message, has_updates = reconcile(api, ROOT, None, False)

        self.assertEqual(api.artifact_requests, [str(newer_play["id"])])
        self.assertTrue(has_updates)
        self.assertIn("play:", message)
        self.assertNotIn("fake-backend:", message)

    def test_unavailable_optional_catch_up_does_not_block_verified_trigger(self):
        main_sha = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        trigger_run = run_record("fake-backend", run_id=123456, sha=main_sha)
        unavailable_run = run_record("play", run_id=234567, sha=main_sha)
        old_fake_backend = run_record("fake-backend", run_id=100, sha=main_sha)
        old_play = run_record("play", run_id=200, sha=main_sha)
        base = pin_files()
        base, _ = update_pin(base, "fake-backend", publication_id(old_fake_backend))
        base, _ = update_pin(base, "play", publication_id(old_play))

        class FakeGitHub:
            def main_sha(self):
                return main_sha

            def workflow_runs(self, family):
                if family == "fake-backend":
                    return [trigger_run]
                if family == "play":
                    return [unavailable_run]
                return []

            def run_attempt(self, run_id, _attempt):
                return {
                    str(trigger_run["id"]): trigger_run,
                    str(unavailable_run["id"]): unavailable_run,
                }[str(run_id)]

            def compare(self, _old, _new):
                return "identical"

            def pr_comments(self, _number):
                return []

        def verify(_api, run, family, _main_sha, _root):
            if family == "play":
                raise CandidateArtifactUnavailable("candidate artifact is missing or expired")
            return (
                run,
                publication_id(run),
                f"https://github.com/{REPOSITORY}/actions/runs/{run['id']}",
            )

        api = FakeGitHub()
        diagnostics = io.StringIO()
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, base, base, None, None),
            ),
            patch("smoke_test_image_adoption.verify_eligible_run", side_effect=verify),
            patch("smoke_test_image_adoption.resolve_pinned_run", return_value=old_fake_backend),
            patch("smoke_test_image_adoption.is_newer_run", return_value=True),
            patch("smoke_test_image_adoption.sys.stderr", diagnostics),
        ):
            message, has_updates = reconcile(
                api,
                ROOT,
                (trigger_run["id"], trigger_run["run_attempt"], trigger_run),
                False,
            )

        self.assertTrue(has_updates)
        self.assertIn("fake-backend:", message)
        self.assertNotIn("play:", message)
        self.assertEqual(image_pin(base, "play"), publication_id(old_play))
        self.assertIn(
            f"play catch-up: publication artifact for run {unavailable_run['id']} "
            f"attempt {unavailable_run['run_attempt']} is unavailable",
            diagnostics.getvalue(),
        )

    def test_unavailable_trigger_publication_is_not_skipped(self):
        main_sha = run_process(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
        trigger_run = run_record(sha=main_sha)
        current = pin_files()

        class FakeGitHub:
            def main_sha(self):
                return main_sha

            def run_attempt(self, _run_id, _attempt):
                return trigger_run

        unavailable = CandidateArtifactUnavailable("candidate artifact is missing or expired")
        with (
            patch(
                "smoke_test_image_adoption.prepare_branch",
                return_value=(main_sha, current, current, None, None),
            ),
            patch(
                "smoke_test_image_adoption.verify_eligible_run",
                side_effect=unavailable,
            ),
            self.assertRaises(CandidateArtifactUnavailable),
        ):
            reconcile(
                FakeGitHub(),
                ROOT,
                (trigger_run["id"], trigger_run["run_attempt"], trigger_run),
                False,
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
