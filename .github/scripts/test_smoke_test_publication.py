import subprocess
import unittest
from unittest.mock import Mock, patch

from smoke_test_publication import create_candidate, inspect_manifest


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.tag = "20261119.123000001"
        self.references = [
            f"ghcr.io/example/project/smoke-test-fake-backend:{self.tag}",
            f"ghcr.io/example/project/smoke-test-fake-backend-windows:{self.tag}",
        ]
        self.inspect = Mock(return_value="sha256:" + "a" * 64)

    def candidate(self, references=None, results=None):
        return create_candidate(
            "fake-backend", "example/project", "a" * 40, "123", "1",
            "example/project/.github/workflows/publish.yml@refs/heads/main", self.tag,
            self.references if references is None else references,
            ["success", "success"] if results is None else results, self.inspect,
        )

    def test_complete_family(self):
        candidate = self.candidate()
        self.assertEqual(candidate["publication_id"], self.tag)
        self.assertEqual(candidate["source_commit"], "a" * 40)
        self.assertEqual(candidate["run_id"], "123")
        self.assertEqual(candidate["family"], "fake-backend")
        self.assertEqual(
            candidate["images"],
            [{"reference": ref, "digest": "sha256:" + "a" * 64}
             for ref in sorted(self.references)],
        )
        self.assertEqual(self.inspect.call_count, 2)

    def test_absent_required_manifest(self):
        self.inspect.side_effect = ["sha256:" + "a" * 64, subprocess.CalledProcessError(1, "docker")]
        with self.assertRaises(subprocess.CalledProcessError):
            self.candidate()

    def test_failed_cancelled_or_skipped_jobs_never_inspect(self):
        for result in ("failure", "cancelled", "skipped"):
            with self.subTest(result=result), self.assertRaises(ValueError):
                self.candidate(results=["success", result])
        self.inspect.assert_not_called()

    def test_empty_duplicate_or_foreign_references_fail(self):
        for references in ([], self.references * 2, ["ghcr.io/other/project/image:" + self.tag]):
            with self.subTest(references=references), self.assertRaises(ValueError):
                self.candidate(references=references)

    def test_missing_or_invalid_digest_fails(self):
        self.inspect.return_value = ""
        with self.assertRaises(ValueError):
            self.candidate()

    @patch("smoke_test_publication.subprocess.run")
    def test_registry_inspection_uses_exact_reference_without_shell(self, run):
        run.return_value.stdout = "sha256:" + "b" * 64 + "\n"
        self.assertEqual(inspect_manifest(self.references[0]), "sha256:" + "b" * 64)
        self.assertEqual(run.call_args.args[0][4], self.references[0])
        self.assertNotIn("shell", run.call_args.kwargs)


if __name__ == "__main__":
    unittest.main()
