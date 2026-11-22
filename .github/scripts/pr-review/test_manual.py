import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import common
import manual
import post
from common import Summary, write_json


class ManualReviewTest(unittest.TestCase):
    def test_only_the_latest_submitted_copilot_review_can_supply_a_hint(self):
        reviews = [[{
            "id": 1, "user": {"login": manual.COPILOT_REVIEWER}, "submitted_at": "2026-01-01",
            "body": "Needs a closer look", "html_url": "old",
        }], [{
            "id": 2, "user": {"login": manual.COPILOT_REVIEWER}, "submitted_at": "2026-01-02",
            "body": "Approval recommended", "html_url": "latest",
        }, {
            "id": 3, "user": {"login": "contributor"}, "submitted_at": "2026-01-03",
            "body": "Needs a closer look", "html_url": "human",
        }]]
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            with mock.patch.object(manual, "gh_json", side_effect=[reviews, [[]]]) as api:
                self.assertEqual(manual.closer_look_context("org/repo", 123, bundle, Summary(pr=123)), "")
                self.assertIn("/reviews/2/comments?", api.call_args.args[0][-1])
            with mock.patch.object(manual, "gh_json", side_effect=[reviews, [[{
                "body": "Needs a closer look", "html_url": "inline",
            }]]]):
                self.assertEqual(
                    manual.closer_look_context("org/repo", 123, bundle, Summary(pr=123)),
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
            write_json(bundle / "metadata.json", {"number": 123, "baseRefOid": "base", "headRefOid": "head"})
            for added in (14, 15):
                with self.subTest(added=added):
                    replacement = "unchanged\n" + "new\n" * added
                    result = post.prepare_manual_review({
                        "body": "Review summary",
                        "comments": [{
                            "kind": "simplification", "path": "Example.java",
                            "start_line": 1, "line": 2, "body": "Simpler code. Update the import.",
                            "suggestion": replacement,
                        }],
                    }, bundle, Summary(pr=123))
                    review = result["review"]
                    self.assertNotIn("event", review)
                    self.assertEqual(review["commit_id"], "head")
                    body = "[Simplification] Simpler code. Update the import."
                    if added == 14:
                        body += "\n\n```suggestion\n" + replacement + "```"
                    self.assertEqual(review["comments"][0]["body"], body)

    def test_pending_review_cannot_post_with_a_different_users_token(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "review-result.json"
            write_json(path, {"pr": 123, "base_sha": "base", "review": {"commit_id": "head", "comments": [{}]}})
            with (
                mock.patch.dict(os.environ, {
                    "REVIEWER": "maintainer", "TRIGGERING_ACTOR": "maintainer", "PR_REVIEW_POST_TOKEN": "test-token",
                }),
                mock.patch.object(manual, "check_snapshot"),
                mock.patch.object(manual, "gh_json", return_value={"login": "someone-else"}),
                mock.patch.object(manual, "post_review") as publish,
                self.assertRaisesRegex(RuntimeError, "does not belong to the dispatcher"),
            ):
                manual.publish_result("org/repo", 123, path, Summary(pr=123))
            publish.assert_not_called()

    def test_empty_review_posts_a_top_level_comment_without_a_user_token(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "review-result.json"
            write_json(path, {"pr": 123, "base_sha": "base", "review": {"commit_id": "head", "comments": []}})
            summary = Summary(pr=123)
            with (
                mock.patch.object(manual, "check_snapshot") as check,
                mock.patch.object(manual, "gh_json", return_value={"html_url": "https://example.test"}) as api,
            ):
                manual.publish_result("org/repo", 123, path, summary)
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
