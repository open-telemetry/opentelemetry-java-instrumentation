# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

import copy
import http.client
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import Mock, patch

import run_benchmarks as runner
import telemetry_receiver as receiver
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import (
    ExportTraceServiceRequest,
)


def exported_spans():
    spans = []
    for index in range(1, 5):
        spans.extend(
            [
                {
                    "trace_id": f"{index:032x}",
                    "span_id": f"{index + 10:016x}",
                    "parent_id": runner.PARENT_ID,
                    "kind": 2,
                    "attributes": {},
                },
                {
                    "trace_id": f"{index:032x}",
                    "span_id": f"{index + 20:016x}",
                    "parent_id": f"{index + 10:016x}",
                    "kind": 3,
                    "attributes": {"db.system.name": "postgresql"},
                },
            ]
        )
    return {"preflight": spans, "errors": 0, "kinds": {"2": 4, "3": 4}}


class RunnerTest(unittest.TestCase):
    def test_cpu_sets_are_disjoint_physical_cores_on_one_node(self):
        topology = "\n".join(
            f"{cpu},{cpu % 64},{cpu // 128},{cpu // 128},Y"
            for cpu in [*range(12), *range(64, 76), 128]
        )
        selected = runner.select_cpus(topology, set(range(256)))
        self.assertEqual(selected["app"], [0, 1])
        self.assertEqual(selected["database"], [2, 3, 4, 5])
        self.assertEqual(selected["load"], [6, 7, 8, 9])
        self.assertEqual(selected["receiver"], [10])
        self.assertEqual(selected["driver"], [11])

    def test_cpu_selection_requires_twelve_allowed_cores(self):
        topology = "\n".join(f"{cpu},{cpu},0,0,Y" for cpu in range(12))
        with self.assertRaises(ValueError):
            runner.select_cpus(topology, set(range(11)))

    def test_schedule_alternates_agents_and_keeps_each_control(self):
        self.assertEqual(
            list(runner.schedule(2)),
            [
                (0, "no-agent"),
                (0, "baseline"),
                (0, "candidate"),
                (1, "no-agent"),
                (1, "candidate"),
                (1, "baseline"),
            ],
        )

    def test_adapted_workload_preserves_mix_and_does_not_mutate_source(self):
        request = {"httpRequest": {"GET": "/legumes", "authority": "localhost:8080"}}
        source = {
            "name": "quarkus-activemq",
            "http": [{"host": "http://localhost:8080", "sharedConnections": 10000}],
            "phases": [
                {"firstReq": {"atOnce": {"scenario": [{"create": [request]}]}}},
                {
                    "rampup": {
                        "increasingRate": {
                            "targetUsersPerSec": 10000,
                            "duration": "5m",
                            "forks": {
                                "createFork": {
                                    "weight": 1,
                                    "scenario": [{"create": [request]}],
                                },
                                "gettersFork": {
                                    "weight": 4,
                                    "scenario": [{"get": [request]}],
                                },
                            },
                        }
                    }
                },
                {
                    "mainPhase": {
                        "constantRate": {
                            "usersPerSec": 10000,
                            "duration": "5m",
                            "startAfter": "rampup",
                        }
                    }
                },
            ],
        }
        original = copy.deepcopy(source)
        actual = runner.workload(source, 2000, 30, 60)
        self.assertEqual(source, original)
        self.assertEqual(
            actual["phases"][1]["rampup"]["increasingRate"]["targetUsersPerSec"], 2000
        )
        self.assertEqual(
            actual["phases"][1]["rampup"]["increasingRate"]["forks"]["gettersFork"][
                "weight"
            ],
            4,
        )
        main = actual["phases"][2]["mainPhase"]["constantRate"]
        self.assertEqual(main["startAfterStrict"], "rampup")
        self.assertNotIn("startAfter", main)
        self.assertEqual(main["duration"], "60s")
        self.assertEqual(
            actual["phases"][0]["firstReq"]["atOnce"]["scenario"][0]["create"][0][
                "httpRequest"
            ]["authority"],
            "127.0.0.1:8080",
        )

    def test_subprocess_helpers_hide_windows_console_and_check_exit(self):
        with (
            patch.object(runner.os, "name", "nt"),
            patch.object(
                runner.subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True
            ),
            patch.object(runner.subprocess, "run") as run,
            patch.object(runner.subprocess, "Popen") as spawn,
        ):
            runner.run(["java", "-version"])
            runner.spawn(["java"])
            self.assertTrue(run.call_args.kwargs["check"])
            self.assertEqual(run.call_args.kwargs["creationflags"], 0x08000000)
            self.assertEqual(spawn.call_args.kwargs["creationflags"], 0x08000000)
            self.assertFalse(spawn.call_args.kwargs["start_new_session"])

    def test_linux_subprocess_has_separate_process_group(self):
        with (
            patch.object(runner.os, "name", "posix"),
            patch.object(runner.subprocess, "Popen") as spawn,
        ):
            runner.spawn(["java"])
            self.assertEqual(spawn.call_args.kwargs["creationflags"], 0)
            self.assertTrue(spawn.call_args.kwargs["start_new_session"])

    def test_processes_are_stopped_and_logs_closed_on_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            process = Mock(pid=123)
            process.poll.return_value = None
            with (
                patch.object(runner, "spawn", return_value=process),
                patch.object(runner.os, "killpg", create=True) as kill,
                self.assertRaisesRegex(ValueError, "expected failure"),
                runner.Processes(Path(directory), {"app": [0, 1]}) as processes,
            ):
                processes.start("app", ["java"])
                raise ValueError("expected failure")
            kill.assert_called_once_with(123, runner.signal.SIGTERM)
            self.assertTrue(processes.logs[0].closed)

    def test_postgres_uses_tcp_even_in_a_long_workspace_path(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(runner.shutil, "chown", create=True),
            patch.object(runner, "run"),
            patch.object(runner, "Processes") as process_class,
        ):
            output = Path(directory) / ("long-workspace-name-" * 7)
            processes = process_class.return_value.__enter__.return_value
            processes.check.side_effect = RuntimeError("stop after database launch")
            with self.assertRaisesRegex(RuntimeError, "stop after database launch"):
                runner.execute(
                    Path(directory), output, {}, "java", "no-agent", 10, 5, 10, "smoke"
                )
            command = processes.start.call_args.args[1]
            self.assertIn("unix_socket_directories=", command)
            self.assertEqual(list(output.glob("postgres-*")), [])

    def test_cpu_interpolation_uses_exact_measurement_boundaries(self):
        samples = [
            {"time_ms": 0, "processes": {"app": [0, 1]}},
            {"time_ms": 1000, "processes": {"app": [2, 1]}},
            {"time_ms": 2000, "processes": {"app": [3, 1]}},
        ]
        self.assertEqual(runner.interpolate(samples, "app", 500), 1)
        self.assertEqual(runner.interpolate(samples, "app", 1500), 2.5)
        with self.assertRaises(ValueError):
            runner.interpolate(samples, "app", 3000)

    def test_disappearing_child_resamples_the_whole_process_tree(self):
        with (
            patch.object(
                runner,
                "read_process_counters",
                side_effect=[FileNotFoundError("exited child"), (1.5, 4096)],
            ) as read,
            patch.object(Path, "exists", return_value=True),
            patch.object(runner.time, "sleep") as sleep,
            patch.object(runner.sys, "stderr"),
        ):
            self.assertEqual(runner.process_counters(42), (1.5, 4096))
            self.assertEqual(read.call_count, 2)
            read.assert_called_with(42)
            sleep.assert_called_once_with(0.01)

    def test_sampling_a_disappeared_root_fails_without_retry(self):
        with (
            patch.object(
                runner, "read_process_counters", side_effect=FileNotFoundError
            ),
            patch.object(Path, "exists", return_value=False),
            patch.object(runner.time, "sleep") as sleep,
            self.assertRaises(FileNotFoundError),
        ):
            runner.process_counters(42)
        sleep.assert_not_called()

    def test_unstable_process_tree_fails_after_bounded_sampling_attempts(self):
        with (
            patch.object(
                runner, "read_process_counters", side_effect=ProcessLookupError
            ) as read,
            patch.object(Path, "exists", return_value=True),
            patch.object(runner.time, "sleep"),
            patch.object(runner.sys, "stderr"),
            self.assertRaisesRegex(RuntimeError, "stable process tree"),
        ):
            runner.process_counters(42)
        self.assertEqual(read.call_count, 5)

    def test_reaped_child_cpu_or_changed_children_require_a_new_snapshot(self):
        stat = ["0"] * 22
        stat[11:15] = ["100", "50", "20", "10"]
        stat[21] = "2"
        for changed_children in (False, True):
            after = stat.copy()
            if not changed_children:
                after[13] = "21"
            values = [
                "42 (root) " + " ".join(stat),
                "",
                "43" if changed_children else "",
                "42 (root) " + " ".join(after),
            ]
            with (
                patch.object(Path, "read_text", side_effect=values),
                patch.object(
                    runner.os,
                    "sysconf",
                    side_effect=lambda name: 100 if name == "SC_CLK_TCK" else 4096,
                    create=True,
                ),
                self.assertRaisesRegex(ProcessLookupError, "changed during sampling"),
            ):
                runner.read_process_counters(42)

    def test_active_and_reaped_descendant_cpu_are_counted_once(self):
        root = ["0"] * 22
        root[11:15] = ["10", "20", "30", "40"]
        root[21] = "2"
        child = ["0"] * 22
        child[11:15] = ["50", "60", "0", "0"]
        child[21] = "3"
        root_stat = "42 (root) " + " ".join(root)
        child_stat = "43 (child) " + " ".join(child)
        with (
            patch.object(
                Path,
                "read_text",
                side_effect=[
                    root_stat,
                    "43",
                    child_stat,
                    "",
                    "",
                    child_stat,
                    "43",
                    root_stat,
                ],
            ),
            patch.object(
                runner.os,
                "sysconf",
                side_effect=lambda name: 100 if name == "SC_CLK_TCK" else 4096,
                create=True,
            ),
        ):
            self.assertEqual(runner.read_process_counters(42), (2.1, 20480))

    def test_hyperfoil_does_not_apply_its_cpu_guard_to_other_services(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(runner.shutil, "chown", create=True),
            patch.object(runner, "run", return_value=Mock(returncode=0)),
            patch.object(runner, "preflight", return_value={}),
            patch.object(
                runner,
                "wait_ready",
                side_effect=[None, None, RuntimeError("stop after load launch")],
            ),
            patch.object(runner, "Processes") as process_class,
        ):
            artifacts = Path(directory)
            (artifacts / "hyperfoil" / "hyperfoil-0.24" / "lib").mkdir(parents=True)
            with self.assertRaisesRegex(RuntimeError, "stop after load launch"):
                runner.execute(
                    artifacts,
                    artifacts / "trial",
                    {},
                    "java",
                    "no-agent",
                    10,
                    5,
                    10,
                    "smoke",
                )
            processes = process_class.return_value.__enter__.return_value
            name, command = processes.start.call_args.args
            self.assertEqual(name, "load")
            self.assertIn("-Dio.hyperfoil.cpu.watchdog.idle.threshold=0", command)

    def test_waits_for_hyperfoil_statistics_to_finish_persisting(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            path = output / "hyperfoil" / "run" / "0000" / "all.json"
            path.parent.mkdir(parents=True)
            path.write_text('{"stats":', encoding="utf-8")

            def finish_writing(_seconds):
                path.write_text('{"stats": []}', encoding="utf-8")

            processes = Mock()
            with patch.object(runner.time, "sleep", side_effect=finish_writing):
                self.assertEqual(
                    runner.persisted_result(output, "0000", processes), {"stats": []}
                )
            self.assertEqual(processes.check.call_count, 2)

    def test_missing_persisted_statistics_fail_explicitly(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(runner.time, "monotonic", side_effect=[0, 31]),
            self.assertRaisesRegex(TimeoutError, "did not finish persisting"),
        ):
            runner.persisted_result(Path(directory), "0000", Mock())

    def measurement(self):
        summaries = []
        for fork, count in (("createFork", 200), ("gettersFork", 800)):
            summaries.append(
                {
                    "phase": "mainPhase",
                    "fork": fork,
                    "total": {
                        "summary": {
                            "startTime": 1000,
                            "endTime": 11000,
                            "requestCount": count,
                            "responseCount": count,
                            "invalid": 0,
                            "connectionErrors": 0,
                            "requestTimeouts": 0,
                            "internalErrors": 0,
                            "blockedTime": 0,
                            "meanResponseTime": 100000,
                            "percentileResponseTime": {"99.0": 200000},
                        }
                    },
                }
            )
        samples = [
            {
                "time_ms": 0,
                "processes": {
                    name: [0, 1024] for name in ("app", "database", "load", "receiver")
                },
            },
            {
                "time_ms": 12000,
                "processes": {
                    name: [1.2, 2048]
                    for name in ("app", "database", "load", "receiver")
                },
            },
        ]
        return {"stats": summaries, "failures": []}, samples

    def analyze(self, result, samples):
        return runner.analyze(
            result,
            samples,
            {name: [0, 1] for name in ("app", "database", "load", "receiver")},
            100,
            10,
        )

    def test_reports_application_cpu_not_whole_host_cpu(self):
        result, samples = self.measurement()
        actual = self.analyze(result, samples)
        self.assertTrue(actual["valid"])
        self.assertAlmostEqual(actual["cpu_us_per_request"], 1000)
        self.assertAlmostEqual(actual["cpu_utilization"]["app"], 0.05)
        self.assertEqual(actual["latency"]["gettersFork"]["p99.0_us"], 200)

    def test_database_expectation_includes_preflight_first_request_and_warmup(self):
        result, samples = self.measurement()
        result["stats"].extend(
            [
                {
                    "phase": "firstReq",
                    "fork": "",
                    "total": {"summary": {"responseCount": 1}},
                },
                {
                    "phase": "rampup",
                    "fork": "createFork",
                    "total": {"summary": {"responseCount": 20}},
                },
            ]
        )
        self.assertEqual(self.analyze(result, samples)["expected_database_rows"], 225)

    def test_errors_blocking_and_missed_load_invalidate_comparison(self):
        for field in (
            "invalid",
            "connectionErrors",
            "requestTimeouts",
            "internalErrors",
            "blockedTime",
        ):
            with self.subTest(field=field):
                result, samples = self.measurement()
                result["stats"][0]["total"]["summary"][field] = 1
                self.assertFalse(self.analyze(result, samples)["valid"])
        result, samples = self.measurement()
        result["stats"][0]["total"]["summary"]["requestCount"] = 100
        self.assertFalse(self.analyze(result, samples)["valid"])

    def test_infrastructure_saturation_invalidates_comparison(self):
        result, samples = self.measurement()
        samples[1]["processes"]["load"][0] = 24
        actual = self.analyze(result, samples)
        self.assertFalse(actual["valid"])
        self.assertIn(
            "load CPU utilization leaves insufficient headroom", actual["errors"]
        )

    def test_requires_both_workload_forks(self):
        result, samples = self.measurement()
        result["stats"].pop()
        with self.assertRaisesRegex(ValueError, "Missing Hyperfoil"):
            self.analyze(result, samples)

    def test_short_trials_allow_stochastic_arrival_variance_but_reject_underload(self):
        result, samples = self.measurement()
        result["stats"][0]["total"]["summary"]["requestCount"] += 72
        result["stats"][0]["total"]["summary"]["responseCount"] += 72
        self.assertTrue(self.analyze(result, samples)["valid"])
        for entry in result["stats"]:
            entry["total"]["summary"]["requestCount"] //= 2
            entry["total"]["summary"]["responseCount"] //= 2
        self.assertFalse(self.analyze(result, samples)["valid"])

    def test_validates_propagated_database_trace(self):
        runner.validate_spans(exported_spans(), "baseline")
        runner.validate_spans({"errors": 0, "kinds": {}}, "no-agent")

    def test_rejects_noop_duplicate_and_orphaned_instrumentation(self):
        with self.assertRaises(ValueError):
            runner.validate_spans(
                {"errors": 0, "kinds": {}, "preflight": []}, "baseline"
            )
        for change in ("duplicate", "orphan", "cycle", "parent", "no-agent"):
            with self.subTest(change=change):
                stats = exported_spans()
                agent = "baseline"
                if change == "duplicate":
                    stats["preflight"].append(stats["preflight"][0])
                elif change == "orphan":
                    stats["preflight"][1]["parent_id"] = "bad"
                elif change == "cycle":
                    stats["preflight"][1]["parent_id"] = stats["preflight"][1][
                        "span_id"
                    ]
                elif change == "parent":
                    stats["preflight"][0]["parent_id"] = ""
                else:
                    agent = "no-agent"
                with self.assertRaises(ValueError):
                    runner.validate_spans(stats, agent)

    def test_instrumented_command_uses_real_export_and_fixed_agent_path(self):
        command = runner.app_command(
            "java", Path("artifacts"), Path("active-agent.jar"), Path("trial"), False
        )
        self.assertIn("-javaagent:active-agent.jar", command)
        self.assertIn("-Dotel.traces.exporter=otlp", command)
        self.assertIn("-Dotel.traces.sampler.arg=0.1", command)
        self.assertNotIn("-Dotel.traces.exporter=none", command)
        self.assertNotIn("StartFlightRecording", " ".join(map(str, command)))
        control = runner.app_command(
            "java", Path("artifacts"), None, Path("trial"), False
        )
        self.assertNotIn("javaagent", " ".join(map(str, control)))

    def test_profiles_are_separate_and_have_unique_paths(self):
        command = runner.app_command(
            "java", Path("artifacts"), Path("active-agent.jar"), Path("trial"), True
        )
        self.assertIn("filename=trial", " ".join(map(str, command)))
        self.assertIn("application.jfr", " ".join(map(str, command)))

    def test_summary_excludes_invalid_trials_without_hiding_them(self):
        rows = [
            {
                "name": agent,
                "agent": agent,
                "rate": 100,
                "repetition": 0,
                "cpu_us_per_request": cost,
                "valid": valid,
                "errors": [] if valid else ["invalid observation"],
            }
            for agent, cost, valid in (
                ("baseline", 100, True),
                ("candidate", 90, True),
                ("no-agent", 9999, False),
            )
        ]
        summary = runner.summarize(rows, False)
        self.assertIn("-10.00%", summary)
        self.assertIn("invalid observation", summary)
        self.assertNotIn("9999", summary)
        self.assertIn("0/1 | invalid", summary)
        self.assertIn(
            "not a steady-state performance comparison",
            runner.summarize(rows, False, "smoke"),
        )


class ReceiverTest(unittest.TestCase):
    def request(self):
        request = ExportTraceServiceRequest()
        scope = request.resource_spans.add().scope_spans.add()
        scope.scope.name = "test"
        span = scope.spans.add()
        span.trace_id = bytes.fromhex("00000000000000000000000000000001")
        span.span_id = bytes.fromhex("1122334455667788")
        span.kind = 2
        attribute = span.attributes.add()
        attribute.key = "db.system.name"
        attribute.value.string_value = "postgresql"
        return request

    def test_receiver_bounds_retained_trace_details(self):
        telemetry = receiver.Telemetry()
        for _ in range(250):
            telemetry.record(self.request())
        stats = telemetry.snapshot()
        self.assertEqual(stats["kinds"], {"2": 250})
        self.assertEqual(stats["scopes"], {"test": 250})
        self.assertEqual(len(stats["preflight"]), 200)

    def test_ordinary_trace_details_are_not_retained(self):
        request = self.request()
        request.resource_spans[0].scope_spans[0].spans[0].trace_id = bytes.fromhex(
            "1234567890abcdef1234567890abcdef"
        )
        telemetry = receiver.Telemetry()
        telemetry.record(request)
        self.assertEqual(telemetry.snapshot()["preflight"], [])
        self.assertEqual(telemetry.snapshot()["kinds"], {"2": 1})

    def test_http_receiver_accepts_valid_otlp_and_reports_decode_failures(self):
        telemetry = receiver.Telemetry()
        with ThreadingHTTPServer(
            ("127.0.0.1", 0), receiver.handler(telemetry)
        ) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                url = f"http://127.0.0.1:{server.server_port}"
                request = urllib.request.Request(
                    url + "/v1/traces",
                    data=self.request().SerializeToString(),
                    headers={"Content-Type": "application/x-protobuf"},
                )
                with urllib.request.urlopen(request) as response:
                    self.assertEqual(response.status, 200)
                    self.assertEqual(response.read(), b"")
                with urllib.request.urlopen(url + "/stats") as response:
                    self.assertEqual(json.load(response)["kinds"], {"2": 1})
                request.data = b"\xff"
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(request)
                self.assertEqual(error.exception.code, 400)
                error.exception.close()
                self.assertEqual(telemetry.snapshot()["errors"], 1)
            finally:
                server.shutdown()
                thread.join()

    def test_receiver_keeps_the_export_connection_alive_between_batches(self):
        telemetry = receiver.Telemetry()
        with ThreadingHTTPServer(
            ("127.0.0.1", 0), receiver.handler(telemetry)
        ) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port)
            try:
                sockets = []
                for _ in range(2):
                    connection.request(
                        "POST",
                        "/v1/traces",
                        self.request().SerializeToString(),
                        {"Content-Type": "application/x-protobuf"},
                    )
                    response = connection.getresponse()
                    self.assertEqual(response.version, 11)
                    self.assertEqual(response.status, 200)
                    self.assertEqual(response.read(), b"")
                    sockets.append(connection.sock)
                self.assertIsNotNone(sockets[0])
                self.assertIs(sockets[0], sockets[1])
                self.assertEqual(telemetry.snapshot()["batches"], 2)
            finally:
                connection.close()
                server.shutdown()
                thread.join()


if __name__ == "__main__":
    unittest.main()
