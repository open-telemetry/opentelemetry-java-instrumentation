/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile

@IgnoreIf({ os.windows })
class NoopApiSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$jdk-20211213.1570880324"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return [
      "OTEL_JAVAAGENT_EXPERIMENTAL_USE_NOOP_API": "true"
    ]
  }

  @Unroll
  def "noop sdk smoke test on JDK #jdk"(int jdk) {
    setup:
    def output = startTarget(jdk)
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION).toString()

    when:
    def response = client().get("/greeting").aggregate().join()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then: "no spans are exported"
    response.contentUtf8() == "Hi!"
    traces.isEmpty()

    then: "javaagent logs its version on startup"
    isVersionLogged(output, currentAgentVersion)


    then: "no metrics are exported"
    def metrics = new MetricsInspector(waitForMetrics())
    metrics.requests.isEmpty()

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }
}

