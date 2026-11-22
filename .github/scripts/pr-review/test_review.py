import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import common
import gate
import post
import review as runner
import sandbox
from common import Summary, write_json


class ReviewTest(unittest.TestCase):
    def test_slash_review_requires_write_access_and_does_not_accept_model_overrides(self):
        env = {
            "GITHUB_EVENT_NAME": "issue_comment", "GITHUB_REPOSITORY": "org/repo",
            "COMMENT_AUTHOR": "maintainer", "COMMENT_BODY": "/review", "PR_NUMBER": "123",
        }
        with mock.patch.object(gate, "gh_json", return_value={"permission": "read"}) as api:
            self.assertIsNone(gate.review_target(env))
            api.assert_called_once_with(["api", "repos/org/repo/collaborators/maintainer/permission"])
            api.return_value = {"permission": "write"}
            self.assertEqual(gate.review_target(env), 123)
            api.reset_mock()
            self.assertIsNone(gate.review_target({**env, "COMMENT_BODY": "/review gpt-5.5"}))
            api.assert_not_called()

    def test_dispatch_and_smoke_test_use_the_provided_pr_number(self):
        for event in ("workflow_dispatch", "push"):
            with self.subTest(event=event), mock.patch.object(gate, "gh_json") as api:
                self.assertEqual(gate.review_target({"GITHUB_EVENT_NAME": event, "PR_NUMBER": "123"}), 123)
                api.assert_not_called()

    def test_prepare_runs_the_three_passes_and_writes_the_checked_result(self):
        metadata = {
            "number": 123, "url": "https://github.com/org/repo/pull/123", "baseRefOid": "base", "headRefOid": "head",
        }

        def build_bundle(pr, bundle, summary):
            write_json(bundle / "diff-scope.json", {})
            return metadata

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "review-result.json"
            with (
                mock.patch.object(runner, "build_bundle", side_effect=build_bundle),
                mock.patch.object(runner, "checkout_pr") as checkout,
                mock.patch.object(runner, "check_snapshot") as check,
                mock.patch.object(runner, "closer_look_context", return_value=", including Needs a closer look https://example.test"),
                mock.patch.object(runner, "model_pass", side_effect=["review", "simplifications", '{"comments":[]}']) as model,
            ):
                runner.prepare_result("org/repo", 123, path, Summary(pr=123))
            first, second, final = [call.args for call in model.call_args_list]
            self.assertEqual([first[:2], second[:2], final[:2]], [(runner.SOL, "max"), (runner.SOL, "max"), (runner.OPUS, "high")])
            self.assertTrue(first[2].startswith(f"review {metadata['url']}, including Needs a closer look"))
            self.assertTrue(second[2].startswith(f"are there any simplifications worth doing in {metadata['url']}?"))
            self.assertIn("review.txt", final[2])
            self.assertIn("simplifications.txt", final[2])
            checkout.assert_called_once_with("org/repo", 123, "head", first[3])
            self.assertEqual(second[3:], first[3:])
            self.assertEqual(final[3:], first[3:])
            self.assertEqual(check.call_count, 2)
            self.assertEqual(post.load_json(path, "result")["review"], {
                "body": "No findings.", "comments": [], "commit_id": "head",
            })

    def test_verifier_retries_a_plain_text_answer(self):
        checkout, bundle = Path("source"), Path("input")
        with mock.patch.object(
            runner, "model_pass", side_effect=["No findings.", '```json\n{"comments":[]}\n```']
        ) as model:
            self.assertEqual(runner.verify_findings("Check both passes.", checkout, bundle), {"comments": []})

        first, retry = [call.args for call in model.call_args_list]
        self.assertEqual(first, (runner.OPUS, "high", "Check both passes.", checkout, bundle))
        self.assertEqual(retry[:2], first[:2])
        self.assertTrue(retry[2].startswith(first[2]))
        self.assertIn("did not return the required JSON", retry[2])
        self.assertEqual(retry[3:], first[3:])

    def test_verifier_fails_closed_after_two_invalid_answers(self):
        with (
            mock.patch.object(runner, "model_pass", side_effect=["No findings.", '{"body":"No findings."}']) as model,
            self.assertRaisesRegex(RuntimeError, "did not return valid findings JSON after two attempts"),
        ):
            runner.verify_findings("Check both passes.", Path("source"), Path("input"))
        self.assertEqual(model.call_count, 2)

    def test_model_pass_uses_the_final_answer_not_progress_or_tool_output(self):
        response = subprocess.CompletedProcess([], 0, stdout=(
            'AWF startup log\n'
            '{"type":"assistant.message","data":{"content":"I will check the code."}}\n'
            '{"type":"tool.execution_complete","data":{"result":{"content":"File contents"}}}\n'
            '{"type":"assistant.message","data":{"content":"{\\"comments\\":[]}"}}\n'
            '{"type":"result","data":{}}\n'
        ))
        with (
            tempfile.TemporaryDirectory() as directory,
            mock.patch.object(runner, "run_in_sandbox", return_value=response) as launch,
        ):
            bundle = Path(directory)
            self.assertEqual(runner.model_pass("model", "high", "prompt", bundle / "checkout", bundle), '{"comments":[]}')
            self.assertEqual((bundle / "prompt.txt").read_text(encoding="utf-8"), "prompt")
            self.assertIn("shell", launch.call_args.args[2])

    def test_model_pass_reports_the_cli_error_from_json(self):
        response = subprocess.CompletedProcess(
            [], 1, stdout='{"type":"session.error","data":{"message":"Model not available."}}\n', stderr=""
        )
        with (
            tempfile.TemporaryDirectory() as directory,
            mock.patch.object(runner, "run_in_sandbox", return_value=response),
            self.assertRaisesRegex(RuntimeError, r"Model not available\."),
        ):
            bundle = Path(directory)
            runner.model_pass("model", "max", "prompt", bundle / "checkout", bundle)

    def test_model_pass_rejects_partial_output_on_timeout(self):
        response = subprocess.CompletedProcess(
            [], 124, stdout='{"type":"assistant.message","data":{"content":"{\\"comments\\":[]}"}}\n',
        )
        with (
            tempfile.TemporaryDirectory() as directory,
            mock.patch.object(runner, "run_in_sandbox", return_value=response),
            self.assertRaisesRegex(RuntimeError, "exceeded its .* sandbox time limit"),
        ):
            bundle = Path(directory)
            runner.model_pass("model", "max", "prompt", bundle / "checkout", bundle)

    def test_checkout_rejects_a_moved_pr_head(self):
        response = subprocess.CompletedProcess([], 0, stdout="e" * 40 + "\n")
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(sandbox, "run", return_value=response) as git:
            with self.assertRaisesRegex(RuntimeError, "PR head changed"):
                sandbox.checkout_pr("org/repo", 123, "d" * 40, Path(directory) / "checkout")
            self.assertEqual(git.call_count, 3)
            self.assertEqual(git.call_args.args[0][-2:], ["rev-parse", "FETCH_HEAD"])

    def test_sandbox_cannot_change_seed_checkout_or_receive_repository_tokens(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            seed = root / "seed"
            seed.mkdir()
            (seed / ".git").mkdir()
            (seed / ".git" / "HEAD").write_text("d" * 40 + "\n", encoding="utf-8")
            (seed / "code").write_text("PR contents", encoding="utf-8")
            inputs = root / "input"
            inputs.mkdir()
            cli = root / "npm" / ".bin" / "copilot"

            def launch(args, *, cwd, env, **kwargs):
                self.assertEqual(args[0], "awf")
                self.assertIn(f"{inputs}:{inputs}:ro", args)
                self.assertEqual(env["GITHUB_WORKSPACE"], str(cwd))
                self.assertEqual(Path(env["HOME"]).parent, cwd.parent)
                self.assertNotIn("GH_TOKEN", env)
                self.assertNotIn("ACTIONS_RUNTIME_TOKEN", env)
                self.assertNotIn("GITHUB_TOKEN", env)
                self.assertEqual((cwd / "code").read_text(encoding="utf-8"), "PR contents")
                (cwd / "code").write_text("scratch edit", encoding="utf-8")

            with (
                mock.patch.dict(os.environ, {
                    "GH_TOKEN": "fixture",
                    "GITHUB_TOKEN": "fixture",
                    "ACTIONS_RUNTIME_TOKEN": "fixture",
                    "JAVA_HOME": "/jdk",
                    "AWF_IMAGE_TAG": "fixture",
                    "RUNNER_TEMP": directory,
                }),
                mock.patch.object(sandbox.shutil, "which", return_value=str(cli)),
                mock.patch.object(sandbox, "run", side_effect=launch) as run,
            ):
                sandbox.run_in_sandbox(seed, inputs, ["--help"])
                sandbox.run_in_sandbox(seed, inputs, ["--help"])
                self.assertNotEqual(run.call_args_list[0].kwargs["cwd"], run.call_args_list[1].kwargs["cwd"])
            self.assertEqual((seed / "code").read_text(encoding="utf-8"), "PR contents")

    def test_only_the_latest_submitted_copilot_review_can_supply_a_hint(self):
        reviews = [[{
            "id": 1, "user": {"login": runner.COPILOT_REVIEWER}, "submitted_at": "2026-01-01",
            "body": "Needs a closer look", "html_url": "old",
        }], [{
            "id": 2, "user": {"login": runner.COPILOT_REVIEWER}, "submitted_at": "2026-01-02",
            "body": "Approval recommended", "html_url": "latest",
        }, {
            "id": 3, "user": {"login": "contributor"}, "submitted_at": "2026-01-03",
            "body": "Needs a closer look", "html_url": "human",
        }]]
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            with mock.patch.object(runner, "gh_json", side_effect=[reviews, [[]]]) as api:
                self.assertEqual(runner.closer_look_context("org/repo", 123, bundle, Summary(pr=123)), "")
                self.assertIn("/reviews/2/comments?", api.call_args.args[0][-1])
            with mock.patch.object(runner, "gh_json", side_effect=[reviews, [[{
                "body": "Needs a closer look", "html_url": "inline",
            }]]]):
                self.assertEqual(
                    runner.closer_look_context("org/repo", 123, bundle, Summary(pr=123)),
                    ", including Needs a closer look inline",
                )

    def test_suggestion_limit_counts_green_lines_and_leaves_the_comment(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            (bundle / "files").mkdir()
            (bundle / "files" / "Example.java").write_text("unchanged\nbefore\n", encoding="utf-8")
            write_json(bundle / "diff-scope.json", {
                "Example.java": {"hunks": [[1, 2]], "changed_lines": [2]},
            })
            metadata = {"number": 123, "baseRefOid": "base", "headRefOid": "head"}
            for added in (14, 15):
                with self.subTest(added=added):
                    replacement = "unchanged\n" + "new\n" * added
                    result = post.prepare_review({
                        "body": "Review summary",
                        "comments": [{
                            "kind": "simplification", "path": "Example.java",
                            "start_line": 1, "line": 2, "body": "Simpler code. Update the import.",
                            "suggestion": replacement,
                        }],
                    }, bundle, metadata, Summary(pr=123))
                    review = result["review"]
                    self.assertNotIn("event", review)
                    self.assertEqual(review["commit_id"], "head")
                    body = "[Simplification] Simpler code. Update the import."
                    if added == 14:
                        body += "\n\n```suggestion\n" + replacement + "```"
                    self.assertEqual(review["comments"][0]["body"], body)

    def test_findings_publish_as_a_comment_review(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "review-result.json"
            review = {
                "commit_id": "head",
                "body": "One finding.",
                "comments": [{"path": "Example.java", "line": 2, "side": "RIGHT", "body": "A finding"}],
            }
            write_json(path, {"pr": 123, "base_sha": "base", "review": review})
            summary = Summary(pr=123)

            def submit(args, current_summary):
                self.assertEqual(args[:5], ["api", "repos/org/repo/pulls/123/reviews", "--method", "POST", "--input"])
                self.assertEqual(post.load_json(Path(args[5]), "review"), {**review, "event": "COMMENT"})
                self.assertIs(current_summary, summary)
                return {"html_url": "https://example.test/review"}

            with (
                mock.patch.object(runner, "check_snapshot") as check,
                mock.patch.object(post, "gh_json", side_effect=submit) as api,
            ):
                runner.publish_result("org/repo", 123, path, summary)
            check.assert_called_once_with(123, "base", "head", summary)
            api.assert_called_once()

    def test_empty_review_posts_a_top_level_comment_without_a_user_token(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "review-result.json"
            write_json(path, {"pr": 123, "base_sha": "base", "review": {"commit_id": "head", "comments": []}})
            summary = Summary(pr=123)
            with (
                mock.patch.object(runner, "check_snapshot") as check,
                mock.patch.object(runner, "gh_json", return_value={"html_url": "https://example.test"}) as api,
            ):
                runner.publish_result("org/repo", 123, path, summary)
            check.assert_called_once_with(123, "base", "head", summary)
            api.assert_called_once_with(
                ["api", "-X", "POST", "repos/org/repo/issues/123/comments", "-f", "body=No findings."], summary
            )

    def test_windows_subprocess_uses_no_window(self):
        with (
            mock.patch.object(common.subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True),
            mock.patch.object(common.subprocess, "run") as run,
        ):
            common.run(["copilot", "--version"])
        self.assertEqual(run.call_args.kwargs["creationflags"], 0x08000000)


if __name__ == "__main__":
    unittest.main()
