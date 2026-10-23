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


class SpliceUnreleasedTest(unittest.TestCase):
    def test_replaces_existing_content(self):
        changelog = """# Changelog

## Unreleased

### 🚫 Deprecations

- Hand-written note without a PR link.

## Version 2.22.0

- Released entry.
"""
        grouped = {key: [] for key, _ in merge.SECTION_ORDER}
        grouped["enhancements"] = [decision(11, "enhancements", "Add generated behavior.")]

        result = merge.splice_unreleased(
            changelog,
            merge.render_generated_block(grouped),
        )

        self.assertNotIn("Hand-written note", result)
        self.assertIn("Add generated behavior.", result)
        self.assertIn("## Version 2.22.0", result)
        self.assertIn("- Released entry.", result)

    def test_renders_sections_in_canonical_order(self):
        grouped = {key: [] for key, _ in merge.SECTION_ORDER}
        grouped["breaking"] = [decision(12, "breaking", "Remove an experimental API.")]
        grouped["enhancements"] = [decision(13, "enhancements", "Add new behavior.")]

        result = merge.render_generated_block(grouped)

        self.assertLess(
            result.index("### ⚠️ Breaking changes to non-stable APIs"),
            result.index("### 📈 Enhancements"),
        )


if __name__ == "__main__":
    unittest.main()
