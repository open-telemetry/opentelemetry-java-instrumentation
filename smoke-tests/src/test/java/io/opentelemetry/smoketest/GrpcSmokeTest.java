/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.time.Duration;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class GrpcSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .image(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-grpc:jdk%s-20251009.18389598594",
                    jdk))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server started.*"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void grpcSmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);
    ManagedChannel channel = null;
    try {
      channel =
          ManagedChannelBuilder.forAddress("localhost", getMappedPort(8080)).usePlaintext().build();
      TraceServiceGrpc.TraceServiceBlockingStub stub = TraceServiceGrpc.newBlockingStub(channel);

      stub.export(ExportTraceServiceRequest.getDefaultInstance());
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("opentelemetry.proto.collector.trace.v1.TraceService/Export")
                          .hasResourceSatisfying(
                              resource ->
                                  resource.hasAttribute(
                                      TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION,
                                      getAgentVersion())),
                  span -> span.hasName("TestService.withSpan")));

      // Verify correct traceIds are logged via MDC instrumentation
      assertThat(output.getLoggedTraceIds()).isEqualTo(getSpanTraceIds());
    } finally {
      if (channel != null) {
        channel.shutdown();
      }
    }
  }
}
