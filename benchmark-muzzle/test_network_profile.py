import unittest

import network_profile


class NetworkProfileTest(unittest.TestCase):
    def test_slow_socket_wait_preserves_timeout_stack_and_zero_bytes(self):
        result = network_profile.summarize([{
            "type": "jdk.SocketRead", "values": {
                "startTime": "2026-11-02T00:00:00Z", "duration": "PT2M0.15S",
                "host": "repo.maven.apache.org", "address": "127.0.0.1", "port": 443,
                "bytesRead": 0, "timeout": 120000, "endOfStream": False,
                "eventThread": {"javaName": "worker"}, "stackTrace": {
                    "frames": [{"method": {"type": {"name": "http/Response"}, "name": "read"}}],
                },
            },
        }])
        self.assertEqual(result["reads_over_10s"], 1)
        self.assertEqual(result["slowest_socket_reads"][0]["seconds"], 120.15)
        self.assertEqual(result["slowest_socket_reads"][0]["stack"], ["http.Response.read"])
        self.assertEqual(result["slowest_socket_reads"][0]["bytes"], 0)
        with self.assertRaisesRegex(ValueError, "no socket-read"):
            network_profile.summarize([])
        self.assertEqual(network_profile.duration_seconds("PT1H2M3.5S"), 3723.5)
        with self.assertRaisesRegex(ValueError, "Invalid"):
            network_profile.duration_seconds("unknown")


if __name__ == "__main__":
    unittest.main()
