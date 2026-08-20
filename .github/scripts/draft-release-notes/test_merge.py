import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("merge.py")
SPEC = importlib.util.spec_from_file_location("draft_release_notes_merge", MODULE_PATH)
merge = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(merge)


def decision(pr, section, bullet):
    return {
        "pr": pr,
        "decision": "include",
        "section": section,
        "bullet": bullet,
    }


class MergeWithExistingTest(unittest.TestCase):
    def test_preserves_existing_content_and_skips_linked_prs(self):
        existing = """## Unreleased

### 🚫 Deprecations

- Hand-written note without a PR link.
- Corrected note
  ([#10](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/10))

### 📈 Enhancements

- Existing enhancement.
"""
        grouped = {key: [] for key, _ in merge.SECTION_ORDER}
        grouped["deprecations"] = [decision(10, "deprecations", "Generated duplicate.")]
        grouped["enhancements"] = [decision(11, "enhancements", "Add new behavior.")]

        result, added, skipped = merge.merge_with_existing(existing, grouped)

        self.assertIn("Hand-written note without a PR link.", result)
        self.assertIn("Corrected note", result)
        self.assertNotIn("Generated duplicate.", result)
        self.assertIn("Add new behavior.", result)
        self.assertEqual(result.count("/pull/10"), 1)
        self.assertEqual(added, 1)
        self.assertEqual(skipped, 1)

    def test_inserts_missing_section_in_canonical_order(self):
        existing = """## Unreleased

### 📈 Enhancements

- Existing enhancement.
"""
        grouped = {key: [] for key, _ in merge.SECTION_ORDER}
        grouped["breaking"] = [decision(12, "breaking", "Remove an experimental API.")]

        result, added, skipped = merge.merge_with_existing(existing, grouped)

        self.assertLess(
            result.index("### ⚠️ Breaking changes to non-stable APIs"),
            result.index("### 📈 Enhancements"),
        )
        self.assertEqual(added, 1)
        self.assertEqual(skipped, 0)

    def test_does_not_treat_pr_link_in_prose_as_existing_entry(self):
        existing = """## Unreleased

### 📈 Enhancements

- Follow up on [#13](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/13).
"""
        grouped = {key: [] for key, _ in merge.SECTION_ORDER}
        grouped["enhancements"] = [decision(13, "enhancements", "Add the actual change.")]

        result, added, skipped = merge.merge_with_existing(existing, grouped)

        self.assertIn("Add the actual change.", result)
        self.assertEqual(added, 1)
        self.assertEqual(skipped, 0)


if __name__ == "__main__":
    unittest.main()
