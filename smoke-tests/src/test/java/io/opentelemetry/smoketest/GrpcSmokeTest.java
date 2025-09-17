/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class GrpcSmokeTest extends JavaSmokeTest {

  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-grpc:jdk"
        + jdk
        + "-20241021.11448062549";
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server started.*");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 23})
  void grpcSmokeTest(int jdk) throws Exception {
    runTarget(
        jdk,
        output -> {
          ManagedChannel channel = null;
          try {
            channel =
                ManagedChannelBuilder.forAddress(
                        "localhost", containerManager.getTargetMappedPort(8080))
                    .usePlaintext()
                    .build();
            TraceServiceGrpc.TraceServiceBlockingStub stub =
                TraceServiceGrpc.newBlockingStub(channel);

            String currentAgentVersion;
            try (JarFile agentJar = new JarFile(agentPath)) {
              currentAgentVersion =
                  agentJar
                      .getManifest()
                      .getMainAttributes()
                      .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            }

            stub.export(ExportTraceServiceRequest.getDefaultInstance());
            List<SpanData> traces = waitForTraces();

            assertThat(traces)
                .hasTracesSatisfyingExactly(
                    trace ->
                        trace.hasSpansSatisfyingExactly(
                            span ->
                                span.hasName(
                                        "opentelemetry.proto.collector.trace.v1.TraceService/Export")
                                    .hasResourceSatisfying(
                                        resource ->
                                            resource.hasAttribute(
                                                TelemetryIncubatingAttributes
                                                    .TELEMETRY_DISTRO_VERSION,
                                                currentAgentVersion)),
                            span -> span.hasName("TestService.withSpan")));

            // Verify correct traceIds are logged via MDC instrumentation
            Set<String> loggedTraceIds = getLoggedTraceIds(output);
            Set<String> spanTraceIds =
                traces.stream().map(SpanData::getTraceId).collect(Collectors.toSet());
            assertThat(loggedTraceIds).isEqualTo(spanTraceIds);

          } finally {
            if (channel != null) {
              channel.shutdown();
            }
          }
        });
  }
}
