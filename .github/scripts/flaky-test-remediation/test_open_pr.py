import importlib.util
import sys
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("3-open-pr.py")
SPEC = importlib.util.spec_from_file_location("flaky_test_remediation_open_pr", MODULE_PATH)
open_pr = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.path.insert(0, str(MODULE_PATH.parent))
SPEC.loader.exec_module(open_pr)


def selected_test():
    return {
        "class": "example.FlakyTest",
        "method": "failsSometimes",
        "fully_qualified": "example.FlakyTest.failsSometimes",
        "flaky_count": 3,
        "source_file": "example/FlakyTest.java",
        "sample_build_id": "primary-build-id",
        "sample_scan_url": "https://develocity.example/s/primary-build-id",
        "sample_failure": "expected true but was false",
        "recent_flaky_scans": [
            {
                "build_id": "other-build-id",
                "scan_url": "https://develocity.example/s/other-build-id",
                "outcome": "flaky",
                "work_unit": "testJava17",
            },
            {
                "build_id": "primary-build-id",
                "scan_url": "https://develocity.example/s/primary-build-id",
                "outcome": "failed",
                "work_unit": "testJava21",
            },
        ],
        "per_day_breakdown": [],
        "window_days": 7,
    }


def render_without_diagnosis(selected):
    with mock.patch.object(open_pr, "DIAGNOSIS") as diagnosis:
        diagnosis.exists.return_value = False
        return open_pr.render(selected)


class RenderTest(unittest.TestCase):
    def test_includes_each_gradle_build_scan_once(self):
        body = render_without_diagnosis(selected_test())

        self.assertIn("### Gradle Build Scans for the flaky failures", body)
        self.assertIn(
            "- [primary-build](https://develocity.example/s/primary-build-id) "
            "(failed, `testJava21`)",
            body,
        )
        self.assertIn(
            "- [other-build-i](https://develocity.example/s/other-build-id) "
            "(flaky, `testJava17`)",
            body,
        )
        self.assertEqual(
            body.count("https://develocity.example/s/primary-build-id"), 1
        )

    def test_includes_primary_scan_when_recent_scans_are_unavailable(self):
        selected = selected_test()
        selected["recent_flaky_scans"] = []

        body = render_without_diagnosis(selected)

        self.assertIn(
            "- [primary-build](https://develocity.example/s/primary-build-id) (failed)",
            body,
        )


if __name__ == "__main__":
    unittest.main()
