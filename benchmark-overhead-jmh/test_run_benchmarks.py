# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import run_benchmarks as runner


class RunnerTest(unittest.TestCase):
    def test_cpu_selection_avoids_smt_siblings_and_other_nodes(self):
        topology = "# CPU,Core,Socket,Node,Online\n0,0,0,0,Y\n64,0,0,0,Y\n"
        topology += "1,1,0,0,Y\n2,2,0,0,Y\n3,3,0,0,Y\n32,32,1,1,Y\n"
        self.assertEqual(
            runner.select_cpus(topology, {0, 1, 2, 3, 32, 64}), [0, 1, 2, 3]
        )

    def test_cpu_selection_respects_affinity_and_online_state(self):
        topology = "0,0,0,0,Y\n1,1,0,0,N\n2,2,0,0,Y\n3,3,0,0,Y\n4,4,0,0,Y\n"
        with self.assertRaises(ValueError):
            runner.select_cpus(topology, {0, 1, 2, 3, 4} - {4})

    def test_schedule_alternates_pairs_and_has_uninstrumented_controls(self):
        runs = list(runner.schedule(3, ("sampled",)))
        self.assertEqual(
            runs,
            [
                (0, "no-agent", "sampled"),
                (0, "baseline", "sampled"),
                (0, "candidate", "sampled"),
                (1, "no-agent", "sampled"),
                (1, "candidate", "sampled"),
                (1, "baseline", "sampled"),
                (2, "no-agent", "sampled"),
                (2, "baseline", "sampled"),
                (2, "candidate", "sampled"),
            ],
        )

    def test_no_agent_command_omits_javaagent(self):
        command = runner.command(
            "java", Path("jmh.jar"), None, "sampled", Path("result.json"), "measure"
        )
        self.assertNotIn("-javaagent:", " ".join(str(value) for value in command))
        self.assertIn("-Dotel.traces.exporter=none", command[-1])
        self.assertNotIn("StartFlightRecording", command[-1])

    def test_profile_command_has_unique_recording(self):
        command = runner.command(
            "java",
            Path("jmh.jar"),
            Path("agent.jar"),
            "sampled",
            Path("unique.json"),
            "profile",
            extension=Path("extension.jar"),
        )
        self.assertIn("filename=unique.jfr", command[-1])
        self.assertIn("-javaagent:agent.jar", command[-1])

    def test_preflight_is_not_measured_or_exporter_free(self):
        command = runner.command(
            "java",
            Path("jmh.jar"),
            Path("agent.jar"),
            "sampled",
            Path("check.json"),
            "smoke",
            preflight=True,
            extension=Path("extension.jar"),
        )
        self.assertIn("-Dotel.traces.exporter=logging", command[-1])
        self.assertNotIn("-Dotel.traces.exporter=none", command[-1])
        self.assertEqual(command[command.index("-wi") + 1], "0")

    def test_instrumented_commands_require_the_span_processor_and_tracing_check(self):
        args = (
            "java",
            Path("jmh.jar"),
            Path("agent.jar"),
            "sampled",
            Path("result.json"),
            "measure",
        )
        with self.assertRaises(ValueError):
            runner.command(*args)
        command = runner.command(*args, extension=Path("extension.jar"))
        self.assertIn("-Dotel.javaagent.extensions=extension.jar", command[-1])
        self.assertIn("-Dbenchmark.verify-tracing=true", command[-1])

    def test_subprocess_errors_are_not_swallowed(self):
        with (
            patch.object(
                runner.subprocess,
                "run",
                side_effect=subprocess.CalledProcessError(1, "java"),
            ),
            self.assertRaises(subprocess.CalledProcessError),
        ):
            runner.run(["java"])

    def test_subprocess_uses_no_window_flag_on_windows(self):
        with (
            patch.object(runner.os, "name", "nt"),
            patch.object(
                runner.subprocess, "CREATE_NO_WINDOW", 0x08000000, create=True
            ),
            patch.object(runner.subprocess, "run") as execute,
        ):
            runner.run(["java", "-version"])
            self.assertEqual(execute.call_args.kwargs["creationflags"], 0x08000000)
            self.assertTrue(execute.call_args.kwargs["check"])

    def test_subprocess_does_not_set_windows_flags_on_linux(self):
        with (
            patch.object(runner.os, "name", "posix"),
            patch.object(runner.subprocess, "run") as execute,
        ):
            runner.run(["java", "-version"])
            self.assertEqual(execute.call_args.kwargs["creationflags"], 0)

    def result(self):
        return {
            "benchmark": runner.PACKAGE + "ServletBenchmark.execute",
            "mode": "sample",
            "forks": 1,
            "threads": 1,
            "primaryMetric": {
                "score": 10,
                "scoreUnit": "us/op",
                "scorePercentiles": {"95.0": 20},
            },
            "secondaryMetrics": {
                "gc.alloc.rate.norm": {"score": 100, "scoreUnit": "B/op"},
                "gc.count": {"score": 0, "scoreUnit": "counts"},
            },
        }

    def read(self, result):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            path.write_text(json.dumps([result]), encoding="utf-8")
            return runner.read_result(path, "sampled")

    def test_reads_gc_and_latency_metrics(self):
        self.assertEqual(
            self.read(self.result()),
            {
                "us_per_op": 10,
                "p95_us": 20,
                "bytes_per_op": 100,
                "gc_count": 0,
                "gc_ms": 0,
            },
        )

    def test_rejects_wrong_benchmark_units_and_nonfinite_results(self):
        for field, value in (
            ("scoreUnit", "ms/op"),
            ("score", float("nan")),
            ("score", -1),
        ):
            with self.subTest(field=field, value=value):
                result = self.result()
                result["primaryMetric"][field] = value
                with self.assertRaises(ValueError):
                    self.read(result)
        result = self.result()
        result["benchmark"] = "unexpected"
        with self.assertRaises(ValueError):
            self.read(result)

    def test_summary_uses_forks_not_request_samples(self):
        rows = []
        for repetition in range(3):
            for agent, time, allocation in (
                ("no-agent", 10, 50),
                ("baseline", 20, 100),
                ("candidate", 18, 90),
            ):
                rows.append(
                    {
                        "repetition": repetition,
                        "agent": agent,
                        "variant": "sampled",
                        "us_per_op": time,
                        "p95_us": time * 2,
                        "bytes_per_op": allocation,
                    }
                )
        summary = runner.summarize(rows, False)
        self.assertIn(
            "| sampled | -10.00% | -10.00% | -10.00%, -10.00%, -10.00% |", summary
        )
        self.assertIn("| candidate | sampled | +8.000 us/op | +80.00% |", summary)
        self.assertNotIn("A/A comparison:", summary)

    def test_identical_artifacts_are_labelled_as_aa(self):
        rows = [
            {
                "repetition": 0,
                "agent": agent,
                "variant": "sampled",
                "us_per_op": 10,
                "p95_us": 20,
                "bytes_per_op": 100,
            }
            for agent in ("no-agent", "baseline", "candidate")
        ]
        self.assertIn("A/A comparison", runner.summarize(rows, True))

    def test_incomplete_pairs_fail(self):
        rows = [
            {
                "repetition": 0,
                "agent": agent,
                "variant": "sampled",
                "us_per_op": 10,
                "p95_us": 20,
                "bytes_per_op": 100,
            }
            for agent in ("no-agent", "baseline")
        ]
        with self.assertRaises(ValueError):
            runner.summarize(rows, False)

    def test_smoke_driver_uses_one_agent_path_and_cleans_it_on_failure(self):
        for failure in (None, "process", "tracing"):
            with (
                self.subTest(failure=failure),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                for name in ("benchmark", "extension", "baseline", "candidate"):
                    (root / f"{name}.jar").write_bytes(name.encode())
                (root / "provenance.json").write_text("{}", encoding="utf-8")
                output = root / "results"
                loaded_agents = []

                def execute(
                    cmd,
                    *,
                    output=output,
                    loaded_agents=loaded_agents,
                    failure=failure,
                    **kwargs,
                ):
                    if cmd[0] == "lscpu":
                        return subprocess.CompletedProcess(
                            cmd,
                            0,
                            stdout="0,0,0,0,Y\n1,1,0,0,Y\n2,2,0,0,Y\n3,3,0,0,Y\n",
                        )
                    if cmd[1] == "-version":
                        return subprocess.CompletedProcess(cmd, 0, stderr="test JDK")
                    active = output / "active-agent.jar"
                    if "-javaagent:" in cmd[-1]:
                        self.assertIn(str(active), cmd[-1])
                        loaded_agents.append(active.read_bytes())
                    else:
                        self.assertFalse(active.exists())
                    if failure == "process":
                        raise subprocess.CalledProcessError(1, cmd)
                    result = self.result()
                    result["benchmark"] = cmd[3].strip("^$")
                    Path(cmd[cmd.index("-rff") + 1]).write_text(
                        json.dumps([result]), encoding="utf-8"
                    )
                    kwargs["stdout"].write("'GET /' SERVER [tracer: test]\n")
                    if failure != "tracing":
                        kwargs["stdout"].write(
                            "Verified active tracing and sampler test\n"
                        )
                    return subprocess.CompletedProcess(cmd, 0)

                argv = ["run_benchmarks.py"]
                argv.extend(["--extension-jar", str(root / "extension.jar")])
                for name in ("benchmark", "baseline", "candidate"):
                    argv.extend(
                        [
                            f"--{name}-jar"
                            if name == "benchmark"
                            else f"--{name}-agent",
                            str(root / f"{name}.jar"),
                        ]
                    )
                argv.extend(
                    [
                        "--provenance",
                        str(root / "provenance.json"),
                        "--output",
                        str(output),
                        "--mode",
                        "smoke",
                    ]
                )
                with (
                    patch("sys.argv", argv),
                    patch.dict(runner.os.environ, {}, clear=True),
                    patch.object(runner.shutil, "which", return_value="java"),
                    patch.object(
                        runner.os,
                        "sched_getaffinity",
                        return_value={0, 1, 2, 3},
                        create=True,
                    ),
                    patch.object(runner.os, "sched_setaffinity", create=True),
                    patch.object(runner, "run", side_effect=execute),
                ):
                    if failure == "process":
                        with self.assertRaises(subprocess.CalledProcessError):
                            runner.main()
                    elif failure == "tracing":
                        with self.assertRaisesRegex(
                            ValueError, "SDK tracing was not verified"
                        ):
                            runner.main()
                    else:
                        runner.main()
                        self.assertEqual(loaded_agents[:2], [b"baseline", b"candidate"])
                        self.assertTrue((output / "forks.csv").is_file())
                self.assertFalse((output / "active-agent.jar").exists())


if __name__ == "__main__":
    unittest.main()
