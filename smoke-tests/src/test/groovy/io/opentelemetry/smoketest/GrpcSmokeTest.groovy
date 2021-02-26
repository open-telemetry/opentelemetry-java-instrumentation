/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import static java.util.stream.Collectors.toSet

import io.grpc.ManagedChannelBuilder
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc
import java.util.jar.Attributes
import java.util.jar.JarFile
import spock.lang.Unroll

class GrpcSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:smoke-grpc-jdk$jdk-20210225.598590600"
  }

  @Unroll
  def "grpc smoke test on JDK #jdk"(int jdk) {
    setup:
    def output = startTarget(jdk)

    def channel = ManagedChannelBuilder.forAddress("localhost", target.getMappedPort(8080))
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
      .map({ bytesToHex(it.getTraceId().toByteArray()) })
      .collect(toSet())
    loggedTraceIds == spanTraceIds

    cleanup:
    stopTarget()
    channel.shutdown()

    where:
    jdk << [8, 11, 15]
  }
}
