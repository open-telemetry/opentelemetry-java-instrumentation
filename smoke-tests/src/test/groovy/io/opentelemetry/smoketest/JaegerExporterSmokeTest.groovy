/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf

import java.util.jar.Attributes
import java.util.jar.JarFile

import static java.util.stream.Collectors.toSet

@IgnoreIf({ os.windows })
class JaegerExporterSmokeTest extends SmokeTest {

    protected String getTargetImage(String jdk) {
        "ghcr.io/open-telemetry/java-test-containers:smoke-springboot-jdk$jdk-20210915.1238472439"
    }

    @Override
    protected Map<String, String> getExtraEnv() {
        return [
                "OTEL_TRACES_EXPORTER"         : "jaeger",
                "OTEL_EXPORTER_JAEGER_ENDPOINT": "http://collector:14250"
        ]
    }

    def "spring boot smoke test with jaeger grpc"() {
        setup:
        startTarget(11)

        def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

        when:
        def response = client().get("/greeting").aggregate().join()
        Collection<ExportTraceServiceRequest> traces = waitForTraces()

        then:
        response.contentUtf8() == "Hi!"
        countSpansByName(traces, '/greeting') == 1
        countSpansByName(traces, 'WebController.greeting') == 1
        countSpansByName(traces, 'WebController.withSpan') == 1

        [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
                .map { it.stringValue }
                .collect(toSet())

        cleanup:
        stopTarget()

    }

}
