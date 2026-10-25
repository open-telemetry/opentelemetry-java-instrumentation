/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.HttpAttributes;
import java.util.List;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PekkoHttpParsingErrorSingletonsTest {

  private static InMemorySpanExporter spanExporter;
  private static OpenTelemetrySdk openTelemetry;

  @BeforeAll
  static void setUp() {
    spanExporter = InMemorySpanExporter.create();
    openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build())
            .build();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @AfterAll
  static void tearDown() {
    openTelemetry.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void markedRejectionIgnoresAmbientServerSpanAndStartsOnlyOnce() {
    HttpRequest request =
        (HttpRequest) org.apache.pekko.http.javadsl.model.HttpRequest.create();
    Context parentContext =
        PekkoHttpServerSingletons.instrumenter().start(Context.root(), request);
    ErrorInfo info = new ErrorInfo("parsing error", "invalid request");
    PekkoHttpParsingErrorSingletons.markParsingError(info);

    try (Scope ignored = parentContext.makeCurrent()) {
      Object[] enter = PekkoHttpParsingErrorSingletons.startSpan(info);
      assertThat(enter).isNotNull();
      assertThat(PekkoHttpParsingErrorSingletons.startSpan(info)).isNull();

      PekkoHttpParsingErrorSingletons.endSpan(
          enter,
          org.apache.pekko.http.javadsl.model.HttpResponse.create().withStatus(400),
          null);
      assertThat(Context.current()).isEqualTo(parentContext);
    }

    PekkoHttpServerSingletons.instrumenter()
        .end(
            parentContext,
            request,
            (HttpResponse) org.apache.pekko.http.javadsl.model.HttpResponse.create(),
            null);

    List<SpanData> finishedSpans = spanExporter.getFinishedSpanItems();
    assertThat(finishedSpans).hasSize(2);
    assertThat(finishedSpans)
        .filteredOn(
            span -> span.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) == 400)
        .singleElement()
        .satisfies(
            span ->
                OpenTelemetryAssertions.assertThat(span)
                    .hasName("HTTP")
                    .hasKind(SpanKind.SERVER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(
                        equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 400L)));
  }
}
