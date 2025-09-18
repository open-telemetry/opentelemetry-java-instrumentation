/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.output.OutputFrame;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootSmokeTest extends JavaSmokeTest {

  public SpringBootSmokeTest() {
    super(
        SmokeTestTarget.springBoot()
            .setServiceName(false)
            .env("OTEL_METRICS_EXPORTER", "otlp")
            .env("OTEL_RESOURCE_ATTRIBUTES", "foo=bar"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 23})
  void springBootSmokeTest(int jdk) throws Exception {
    Consumer<OutputFrame> output = startTarget(jdk);
    String currentAgentVersion;
    try (JarFile agentJar = new JarFile(agentPath)) {
      currentAgentVersion =
          agentJar
              .getManifest()
              .getMainAttributes()
              .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }

    var response = client().get("/greeting").aggregate().join();
    assertThat(response.contentUtf8()).isEqualTo("Hi!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /greeting")
                        .hasAttribute(
                            satisfies(ThreadIncubatingAttributes.THREAD_ID, a -> a.isNotNull()))
                        .hasAttribute(
                            satisfies(ThreadIncubatingAttributes.THREAD_NAME, a -> a.isNotBlank()))
                        .hasResourceSatisfying(
                            resource ->
                                resource
                                    .hasAttribute(
                                        TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION,
                                        currentAgentVersion)
                                    .hasAttribute(
                                        satisfies(
                                            OsIncubatingAttributes.OS_TYPE, a -> a.isNotNull()))
                                    .hasAttribute(AttributeKey.stringKey("foo"), "bar")
                                    .hasAttribute(
                                        ServiceAttributes.SERVICE_NAME, "otel-spring-test-app")
                                    .hasAttribute(
                                        ServiceAttributes.SERVICE_VERSION,
                                        "2.10.0-alpha-SNAPSHOT")),
                span -> span.hasName("WebController.withSpan")));

    // Check agent version is logged on startup
    assertVersionLogged(output, currentAgentVersion);

    // Check trace IDs are logged via MDC instrumentation
    Set<String> loggedTraceIds = getLoggedTraceIds(output);
    List<SpanData> spans = testing.spans();
    Set<String> spanTraceIds = spans.stream().map(SpanData::getTraceId).collect(Collectors.toSet());
    assertThat(loggedTraceIds).isEqualTo(spanTraceIds);

    // Check JVM metrics are exported
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"));
  }
}
