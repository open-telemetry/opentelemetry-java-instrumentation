import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import common


class DownloadActionsJobLogTest(unittest.TestCase):
    def test_allows_escape_sequences_and_preserves_log_bytes(self):
        log_bytes = b"\x1b[31mred text\x1b[0m\n"

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "job.log"

            def write_log(cmd, **kwargs):
                kwargs["stdout"].write(log_bytes)

            with mock.patch.object(common.subprocess, "run", side_effect=write_log) as run:
                common.download_actions_job_log("owner", "repo", 123, path, common.Summary(pr=1))
            self.assertEqual(path.read_bytes(), log_bytes)

        self.assertEqual(
            run.call_args.args[0],
            [
                "gh",
                "api",
                "--allow-escape-sequences",
                "-H",
                "Accept: application/vnd.github+json",
                "repos/owner/repo/actions/jobs/123/logs",
            ],
        )
        self.assertTrue(run.call_args.kwargs["check"])

    def test_download_failure_is_not_swallowed(self):
        failure = subprocess.CalledProcessError(1, ["gh", "api"], stderr="download failed")

        with (
            tempfile.TemporaryDirectory() as directory,
            mock.patch.object(common.subprocess, "run", side_effect=failure),
        ):
            path = Path(directory) / "job.log"
            with self.assertRaises(subprocess.CalledProcessError) as error:
                common.download_actions_job_log("owner", "repo", 123, path, common.Summary(pr=1))
        self.assertEqual(error.exception.stderr, "download failed")


if __name__ == "__main__":
    unittest.main()
