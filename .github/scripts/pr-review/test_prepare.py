import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import prepare


class ReviewBundleTest(unittest.TestCase):
    def test_added_line_starting_with_two_pluses_is_not_a_file_header(self):
        scope = prepare.parse_diff(
            "diff --git a/example.txt b/example.txt\n"
            "--- a/example.txt\n+++ b/example.txt\n@@ -0,0 +1,2 @@\n"
            "+++ text\n+more text\n"
        )
        self.assertEqual(list(scope), ["example.txt"])
        self.assertEqual(scope["example.txt"]["changed_lines"], [1, 2])

    def test_fetches_changed_files_from_the_head_repository(self):
        metadata = {
            "headRepositoryOwner": {"login": "contributor"},
            "headRepository": {"name": "instrumentation"},
            "headRefOid": "head-sha",
        }
        source = "instrumentation/example/Example.java"
        instructions = ".github/instructions/example.instructions.md"
        scope = {
            source: {"changed_lines": [2], "hunks": [[1, 3]], "status": "modified"},
            instructions: {"changed_lines": [1], "hunks": [[1, 1]], "status": "added"},
            "removed.java": {"changed_lines": [], "hunks": [], "status": "deleted"},
            "licenses/example.txt": {"changed_lines": [1], "hunks": [[1, 1]], "status": "added"},
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(
                prepare, "read_file_excerpt", side_effect=lambda repo, ref, path: path
            ) as read_file:
                prepare.write_review_bundle(metadata, "the diff", scope, root)

            self.assertCountEqual(
                read_file.call_args_list,
                [
                    mock.call("contributor/instrumentation", "head-sha", source),
                    mock.call("contributor/instrumentation", "head-sha", instructions),
                ],
            )
            self.assertEqual(
                {path.name for path in root.iterdir()},
                {"files", "pr.diff", "diff-scope.json"},
            )
            self.assertEqual((root / "files" / source).read_text(encoding="utf-8"), source)
            self.assertEqual(
                (root / "files" / instructions).read_text(encoding="utf-8"), instructions
            )
            self.assertEqual((root / "pr.diff").read_text(encoding="utf-8"), "the diff")
            self.assertEqual(
                json.loads((root / "diff-scope.json").read_text(encoding="utf-8")), scope
            )

    def test_does_not_fetch_files_for_a_deletion_only_bundle(self):
        metadata = {
            "headRepositoryOwner": {"login": "contributor"},
            "headRepository": {"name": "instrumentation"},
            "headRefOid": "head-sha",
        }
        scope = {"removed.java": {"changed_lines": [], "hunks": [], "status": "deleted"}}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(prepare, "read_file_excerpt") as read_file:
                prepare.write_review_bundle(metadata, "the diff", scope, root)

            read_file.assert_not_called()
            self.assertFalse((root / "files").exists())

    def test_fetch_failure_is_not_reported_as_a_prepared_bundle(self):
        metadata = {
            "headRepositoryOwner": {"login": "contributor"},
            "headRepository": {"name": "instrumentation"},
            "headRefOid": "head-sha",
        }
        scope = {"Example.java": {"changed_lines": [1], "hunks": [[1, 1]], "status": "added"}}
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.object(
                prepare, "read_file_excerpt", side_effect=RuntimeError("content unavailable")
            ):
                with self.assertRaisesRegex(RuntimeError, "content unavailable"):
                    prepare.write_review_bundle(metadata, "the diff", scope, Path(directory))
            self.assertFalse((Path(directory) / "diff-scope.json").exists())


if __name__ == "__main__":
    unittest.main()
