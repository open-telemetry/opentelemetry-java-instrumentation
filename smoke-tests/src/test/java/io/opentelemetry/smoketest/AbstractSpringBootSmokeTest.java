/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_DISTRO_VERSION;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;

abstract class AbstractSpringBootSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot()
        .setServiceName(false)
        .env("OTEL_METRICS_EXPORTER", "otlp")
        .env("OTEL_RESOURCE_ATTRIBUTES", "foo=bar");
  }

  protected final void assertSpringBootTelemetry(SmokeTestOutput output) {
    var response = client().get("/greeting").aggregate().join();
    assertThat(response.contentUtf8()).isEqualTo("Hi!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /greeting")
                        .hasAttribute(satisfies(THREAD_ID, val -> val.isNotNull()))
                        .hasAttribute(satisfies(THREAD_NAME, val -> val.isNotBlank()))
                        .hasResourceSatisfying(
                            resource ->
                                resource
                                    .hasAttribute(TELEMETRY_DISTRO_VERSION, getAgentVersion())
                                    .hasAttribute(satisfies(OS_TYPE, val -> val.isNotNull()))
                                    .hasAttribute(stringKey("foo"), "bar")
                                    .hasAttribute(SERVICE_NAME, "otel-spring-test-app")
                                    .hasAttribute(SERVICE_VERSION, "1.2.3")),
                span -> span.hasName("WebController.withSpan")));

    output.assertAgentVersionLogged();
    assertThat(output.getLoggedTraceIds()).isEqualTo(getSpanTraceIds());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"));
  }
}
