import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("classify.py")
SPEC = importlib.util.spec_from_file_location("draft_release_notes_classify", MODULE_PATH)
classify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = classify
SPEC.loader.exec_module(classify)


class PreclassifyTest(unittest.TestCase):
    def test_preserves_prs_with_hand_written_changelog_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={
                    "files": [
                        {"path": "CHANGELOG.md"},
                        {"path": "instrumentation/example/src/main/java/Example.java"},
                    ],
                    "changes_unreleased": True,
                    "labels": [],
                    "touches_src_main": True,
                },
                diff="",
            )

            result = classify.preclassify(bundle)

        self.assertEqual(result["decision"], "omit")
        self.assertEqual(result["surface"], "hand-written changelog entry")

    def test_changed_paths_accepts_git_fallback_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"files": ["CHANGELOG.md"]},
                diff="",
            )

            self.assertEqual(classify.changed_paths(bundle), ["CHANGELOG.md"])


if __name__ == "__main__":
    unittest.main()
