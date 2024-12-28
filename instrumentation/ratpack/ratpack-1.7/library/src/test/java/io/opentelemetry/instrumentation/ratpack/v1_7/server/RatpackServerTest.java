package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class RatpackServerTest {

  private static InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
  private SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
      .build();

  OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
      .setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance()))
      .setTracerProvider(tracerProvider).build();

  RatpackServerTelemetry telemetry = RatpackServerTelemetry.create(openTelemetry);

  @AfterAll
  static void cleanup() {
    spanExporter.reset();
  }

  @Test
  void testAddSpanOnHandlers() {


  }


}
