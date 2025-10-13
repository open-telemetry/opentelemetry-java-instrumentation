/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot("20251013.18455512027")
        .setServiceName(false)
        .env("OTEL_METRICS_EXPORTER", "otlp")
        .env("OTEL_RESOURCE_ATTRIBUTES", "foo=bar");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void springBootSmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);

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
                                        getAgentVersion())
                                    .hasAttribute(
                                        satisfies(
                                            OsIncubatingAttributes.OS_TYPE, a -> a.isNotNull()))
                                    .hasAttribute(AttributeKey.stringKey("foo"), "bar")
                                    .hasAttribute(
                                        ServiceAttributes.SERVICE_NAME, "otel-spring-test-app")
                                    .hasAttribute(
                                        ServiceAttributes.SERVICE_VERSION,
                                        "1.2.3")),
                span -> span.hasName("WebController.withSpan")));

    // Check agent version is logged on startup
    output.assertAgentVersionLogged();

    // Check trace IDs are logged via MDC instrumentation
    assertThat(output.getLoggedTraceIds()).isEqualTo(getSpanTraceIds());

    // Check JVM metrics are exported
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"));
  }
}
