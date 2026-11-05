import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import prepare
from common import Summary


class ReviewBundleTest(unittest.TestCase):
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
        summary = Summary(pr=123)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(
                prepare, "read_file_excerpt", side_effect=lambda repo, ref, path, summary: path
            ) as read_file:
                prepare.write_review_bundle(metadata, "the diff", scope, root, summary)

            self.assertCountEqual(
                read_file.call_args_list,
                [
                    mock.call("contributor/instrumentation", "head-sha", source, None),
                    mock.call("contributor/instrumentation", "head-sha", instructions, None),
                ],
            )
            self.assertEqual(
                {path.name for path in root.iterdir()},
                {"files", "pr.diff", "metadata.json", "diff-scope.json"},
            )
            self.assertEqual((root / "files" / source).read_text(encoding="utf-8"), source)
            self.assertEqual(
                (root / "files" / instructions).read_text(encoding="utf-8"), instructions
            )
            self.assertEqual((root / "pr.diff").read_text(encoding="utf-8"), "the diff")
            self.assertEqual(
                json.loads((root / "metadata.json").read_text(encoding="utf-8")), metadata
            )
            self.assertEqual(
                json.loads((root / "diff-scope.json").read_text(encoding="utf-8")), scope
            )
            self.assertEqual(summary.temp_dir, directory)

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
                prepare.write_review_bundle(metadata, "the diff", scope, root, Summary(pr=123))

            read_file.assert_not_called()
            self.assertEqual(list((root / "files").iterdir()), [])

    def test_fetch_failure_is_not_reported_as_a_prepared_bundle(self):
        metadata = {
            "headRepositoryOwner": {"login": "contributor"},
            "headRepository": {"name": "instrumentation"},
            "headRefOid": "head-sha",
        }
        scope = {"Example.java": {"changed_lines": [1], "hunks": [[1, 1]], "status": "added"}}
        summary = Summary(pr=123)
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.object(
                prepare, "read_file_excerpt", side_effect=RuntimeError("content unavailable")
            ):
                with self.assertRaisesRegex(RuntimeError, "content unavailable"):
                    prepare.write_review_bundle(metadata, "the diff", scope, Path(directory), summary)

            self.assertIsNone(summary.temp_dir)

    def test_prompt_lists_added_instructions_and_deleted_files(self):
        instructions = ".github/instructions/example.instructions.md"
        scope = {
            instructions: {"changed_lines": [1], "hunks": [[1, 1]], "status": "added"},
            "removed.java": {"changed_lines": [], "hunks": [], "status": "deleted"},
        }
        prompt = prepare.render_prompt(Path("bundle"), Path("findings.json"), "main", scope)

        self.assertIn(f"- PR-changed file contents: {Path('bundle')}/files/<repo-relative-path>", prompt)
        self.assertIn(f"- {instructions}", prompt)
        self.assertIn("Deleted by this PR (do not read):\n- removed.java", prompt)
        self.assertIn("- Findings JSON output path: findings.json", prompt)


if __name__ == "__main__":
    unittest.main()
