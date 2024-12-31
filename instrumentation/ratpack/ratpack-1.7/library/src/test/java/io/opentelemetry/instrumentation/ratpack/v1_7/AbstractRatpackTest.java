/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;

public abstract class AbstractRatpackTest {
  protected final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
  private final SdkTracerProvider tracerProvider =
      SdkTracerProvider.builder()
          .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
          .build();

  protected OpenTelemetry openTelemetry =
      OpenTelemetrySdk.builder()
          .setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance()))
          .setTracerProvider(tracerProvider)
          .build();

  protected RatpackClientTelemetry telemetry = RatpackClientTelemetry.create(openTelemetry);
  protected RatpackServerTelemetry serverTelemetry = RatpackServerTelemetry.create(openTelemetry);

  @AfterEach
  void cleanup() {
    spanExporter.reset();
  }

  protected SpanData findSpanData(String spanName, SpanKind spanKind) {
    return spanExporter.getFinishedSpanItems().stream()
        .filter(span -> spanName.equals(span.getName()) && span.getKind().equals(spanKind))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Span not found"));
  }
}
