package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;
import spock.util.concurrent.PollingConditions;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
  void testAddSpanOnHandlers() throws Exception {
    EmbeddedApp app = EmbeddedApp.of(
        spec -> {
          spec.registry(registry -> Registry.of(regSpec -> telemetry.configureRegistry(regSpec)));
          spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("hi-foo")));
        }
    );

    app.test( httpClient -> {
      assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("hi-foo");
      new PollingConditions().eventually(() -> {
        Map<String, SpanData> spans = spanExporter.getFinishedSpanItems().stream()
            .collect(Collectors.toMap(SpanData::getName, span -> span));
        assertThat(spans).containsKey("GET /foo");


    });

  }

}
