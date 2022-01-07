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

import static io.opentelemetry.smoketest.TestContainerManager.useLinuxContainers
import static java.util.stream.Collectors.toSet

@IgnoreIf({ !useLinuxContainers() })
class QuarkusSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk$jdk-20211213.1574595137"
  }

  @Unroll
  def "quarkus smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    client().get("/hello").aggregate().join()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    countSpansByName(traces, '/hello') == 1
    countSpansByName(traces, 'HelloResource.hello') == 1

    [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
      .map { it.stringValue }
      .collect(toSet())

    cleanup:
    stopTarget()

    where:
    jdk << [11, 17] // Quarkus 2.0+ does not support Java 8
  }
}
