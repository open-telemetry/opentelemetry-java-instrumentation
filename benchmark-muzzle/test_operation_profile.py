import json
from pathlib import Path
import tempfile
import unittest

import operation_profile


class OperationProfileTest(unittest.TestCase):
    def test_overlapping_operations_do_not_double_count_wall_time(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "operations.jsonl"
            records = [
                {"start": 10, "end": 110, "category": "configuration", "name": "configure"},
                {"start": 20, "end": 90, "category": "resolution", "name": "resolve"},
                {"start": 30, "end": 50, "category": "transfer", "name": "read.jar", "bytes": 42},
                {"start": 40, "end": 60, "category": "transfer", "name": "read.pom", "missing": True},
                {"start": 70, "end": 100, "category": "assertion", "name": "muzzle-AssertPass"},
            ]
            path.write_text("\n".join(json.dumps(record) for record in records))
            result = operation_profile.summarize(path, 120)
            self.assertEqual(result["exclusive_wall_ms"], {
                "configuration": 20, "resolution": 40, "transfer": 30, "assertion": 10, "unattributed": 20,
            })
            self.assertEqual(sum(result["exclusive_wall_ms"].values()), 120)
            self.assertEqual(result["transfer_bytes"], 42)
            self.assertEqual(result["missing_transfer_operations"], 1)
            self.assertEqual(json.loads(json.dumps(result)), result)


if __name__ == "__main__":
    unittest.main()
