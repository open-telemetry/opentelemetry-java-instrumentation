import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("fetch.py")
SPEC = importlib.util.spec_from_file_location("draft_release_notes_fetch", MODULE_PATH)
fetch = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = fetch
SPEC.loader.exec_module(fetch)


class PatchAddsLinesInRangeTest(unittest.TestCase):
    def test_detects_added_line_inside_range(self):
        patch = """@@ -8,2 +8,3 @@
 context
+added
 context
"""
        self.assertTrue(fetch.patch_adds_lines_in_range(patch, 8, 11))

    def test_ignores_added_line_outside_range(self):
        patch = """@@ -20,2 +20,3 @@
 context
+added
 context
"""
        self.assertFalse(fetch.patch_adds_lines_in_range(patch, 8, 11))


if __name__ == "__main__":
    unittest.main()
