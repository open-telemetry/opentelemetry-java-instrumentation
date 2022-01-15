/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.grpc.ManagedChannelBuilder
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers
import static java.util.stream.Collectors.toSet

@IgnoreIf({ useWindowsContainers() })
class GrpcSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-grpc:jdk$jdk-20211213.1570880329"
  }

  @Unroll
  def "grpc smoke test on JDK #jdk"(int jdk) {
    setup:
    def output = startTarget(jdk)

    def channel = ManagedChannelBuilder.forAddress("localhost", containerManager.getTargetMappedPort(8080))
      .usePlaintext()
      .build()
    def stub = TraceServiceGrpc.newBlockingStub(channel)

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    stub.export(ExportTraceServiceRequest.getDefaultInstance())
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    countSpansByName(traces, 'opentelemetry.proto.collector.trace.v1.TraceService/Export') == 1
    countSpansByName(traces, 'TestService.withSpan') == 1

    [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
      .map { it.stringValue }
      .collect(toSet())

    then: "correct traceIds are logged via MDC instrumentation"
    def loggedTraceIds = getLoggedTraceIds(output)
    def spanTraceIds = getSpanStream(traces)
      .map({ TraceId.fromBytes(it.getTraceId().toByteArray()) })
      .collect(toSet())
    loggedTraceIds == spanTraceIds

    cleanup:
    stopTarget()
    channel.shutdown()

    where:
    jdk << [8, 11, 17]
  }
}
