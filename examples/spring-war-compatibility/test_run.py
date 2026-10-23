# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Classifier tests use log text, not a synthetic application failure."""

import subprocess
import unittest
from unittest.mock import patch

from run import HELLO, classify, docker


class ClassifierTest(unittest.TestCase):
    def test_docker_uses_no_window_launcher(self):
        for platform, flags in (("nt", 0x08000000), ("posix", 0)):
            with self.subTest(platform=platform):
                with (
                    patch("run.os.name", platform),
                    patch("run.subprocess.CREATE_NO_WINDOW", 0x08000000, create=True),
                    patch(
                        "run.subprocess.run",
                        return_value=subprocess.CompletedProcess([], 0),
                    ) as launch,
                ):
                    docker("version")
                self.assertEqual(["docker", "version"], launch.call_args.args[0])
                self.assertEqual(flags, launch.call_args.kwargs["creationflags"])

    def test_healthy_control_requires_exact_response(self):
        self.assertEqual(
            "healthy", classify("", 200, HELLO, "none", "otelAutoDispatcherFilter=false\n")
        )
        self.assertEqual(
            "other-startup-or-http-failure", classify("", 200, "wrong", "none", "")
        )

    def test_agent_must_be_active(self):
        self.assertEqual(
            "diagnostics-or-instrumentation-failure",
            classify("", 200, HELLO, "2.10.0-indy", "otelAutoDispatcherFilter=true\n"),
        )
        self.assertEqual(
            "healthy",
            classify(
                "opentelemetry-javaagent - version: 2.10.0",
                200,
                HELLO,
                "2.10.0-indy",
                "otelAutoDispatcherFilter=true\n",
            ),
        )

    def test_requestpath_constraint_at_reported_site(self):
        log = (
            'java.lang.LinkageError: loader constraint violation: loader (instance of '
            'java/net/URLClassLoader) previously initiated loading for a different type '
            'with name "org/springframework/http/server/RequestPath"\n'
            'at org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter.parseAndCacheMh\n'
            'at org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter.<clinit>\n'
            "Error creating bean with name 'otelAutoDispatcherFilter'\n"
            'at org.springframework.web.context.ContextLoaderListener.contextInitialized\n'
        )
        self.assertEqual(
            "requestpath-loader-constraint", classify(log, 404, "", "2.10.0", "")
        )
        self.assertEqual(
            "requestpath-loader-constraint-other-site",
            classify(log.split("\n")[0], 404, "", "2.10.0", ""),
        )

    def test_other_errors_are_not_a_reproduction(self):
        for log in (
            "java.lang.NoSuchMethodError: org.springframework.http.server.RequestPath",
            "java.lang.LinkageError: loader constraint violation: some.other.Type",
            "Context initialization failed",
            "java.lang.LinkageError: unrelated\norg.springframework.http.server.RequestPath",
        ):
            with self.subTest(log=log):
                self.assertEqual(
                    "other-startup-or-http-failure", classify(log, 404, "", "2.10.0", "")
                )


if __name__ == "__main__":
    unittest.main()
