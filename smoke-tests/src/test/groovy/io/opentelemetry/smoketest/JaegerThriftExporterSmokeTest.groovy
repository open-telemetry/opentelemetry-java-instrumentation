/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import okhttp3.Request

import java.util.jar.Attributes
import java.util.jar.JarFile

import static java.util.stream.Collectors.toSet

class JaegerThriftExporterSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:smoke-springboot-jdk$jdk-20201217.427450994"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return [
      "OTEL_EXPORTER"                : "jaeger-thrift",
      "OTEL_EXPORTER_JAEGER_ENDPOINT": "http://collector:14268/api/traces"
    ]
  }

  def "spring boot smoke test with jaeger thrift"() {
    setup:
    startTarget(11)

    String url = "http://localhost:${target.getMappedPort(8080)}/greeting"
    def request = new Request.Builder().url(url).get().build()

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.body().string() == "Hi!"
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
