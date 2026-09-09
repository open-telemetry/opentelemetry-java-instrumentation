import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("1-select-flaky-test.py")
SPEC = importlib.util.spec_from_file_location(
    "flaky_test_remediation_select", MODULE_PATH
)
select_flaky_test = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.path.insert(0, str(MODULE_PATH.parent))
SPEC.loader.exec_module(select_flaky_test)


def history(flaky_count, *build_ids):
    return {
        "outcomeTrend": {
            "dataPoints": [
                {"outcomeDistribution": {"flaky": flaky_count}}
            ]
        },
        "testResults": [
            {
                "buildId": build_id,
                "outcome": "flaky",
                "startTimestamp": index,
            }
            for index, build_id in enumerate(build_ids)
        ],
    }


class CollectFlakyScansByTimeTest(unittest.TestCase):
    def test_splits_window_when_trend_exceeds_visible_flaky_results(self):
        histories = {
            (0, 999999): history(5, "scan-2"),
            (500000, 999999): history(2, "scan-4", "scan-5"),
            (0, 499999): history(3, "scan-2"),
            (250000, 499999): history(1, "scan-3"),
        }
        requested_windows = []

        def fetch_history(since_ms, until_ms):
            requested_windows.append((since_ms, until_ms))
            return histories[(since_ms, until_ms)]

        _, _, scans = select_flaky_test.collect_flaky_scans_by_time(
            fetch_history,
            base="https://develocity.example",
            since_ms=0,
            until_ms=999999,
            limit=4,
            seen={"scan-1"},
        )

        self.assertEqual(
            [scan["build_id"] for scan in scans],
            ["scan-2", "scan-4", "scan-5", "scan-3"],
        )
        self.assertEqual(
            requested_windows,
            [
                (0, 999999),
                (500000, 999999),
                (0, 499999),
                (250000, 499999),
            ],
        )


if __name__ == "__main__":
    unittest.main()
