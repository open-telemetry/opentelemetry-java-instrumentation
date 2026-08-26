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


class UserFacingSourceTest(unittest.TestCase):
    def test_detects_runtime_source(self):
        self.assertTrue(
            fetch.touches_user_facing_src_main(
                ["instrumentation/example/src/main/java/Example.java"]
            )
        )

    def test_ignores_testing_source(self):
        self.assertFalse(
            fetch.touches_user_facing_src_main(
                ["instrumentation/example/testing/src/main/java/TestHelper.java"]
            )
        )


if __name__ == "__main__":
    unittest.main()
