/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_SDK_LANGUAGE;
import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_SDK_NAME;
import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_SDK_VERSION;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests the resource providers in javaagent-tooling: DistroComponentProvider
 * andResourceCustomizerProvider
 */
class ResourceTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void distroAndServiceResourceAttributes() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasResourceSatisfying(
                            r ->
                                r.hasAttributesSatisfyingExactly(
                                    equalTo(TELEMETRY_SDK_LANGUAGE, "java"),
                                    equalTo(TELEMETRY_SDK_NAME, "opentelemetry"),
                                    satisfies(TELEMETRY_SDK_VERSION, v -> v.isNotBlank()),
                                    equalTo(SERVICE_NAME, "unknown_service:java"),
                                    satisfies(SERVICE_INSTANCE_ID, v -> v.isNotBlank()),
                                    equalTo(
                                        TELEMETRY_DISTRO_NAME,
                                        "opentelemetry-java-instrumentation"),
                                    satisfies(TELEMETRY_DISTRO_VERSION, v -> v.isNotBlank())))));
  }
}
