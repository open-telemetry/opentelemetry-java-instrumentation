/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouchbaseTracerTest {

  private InMemorySpanExporter exporter;
  private SdkTracerProvider tracerProvider;
  private Tracer tracer;

  @BeforeEach
  void setUp() {
    exporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test");
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void createsRootSpanWhenCurrentContextIsDisabled() {
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan span =
          new CouchbaseTracer(tracer, false, INTERNAL, false).startSpan("request", null);
      span.end();
    }
    current.end();

    SpanData request = findSpan("request");
    assertThat(request.getParentSpanContext().isValid()).isFalse();
  }

  @Test
  void inheritsCurrentContextWhenEnabled() {
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan span =
          new CouchbaseTracer(tracer, true, INTERNAL, false).startSpan("request", null);
      span.end();
    }
    current.end();

    assertThat(findSpan("request").getParentSpanId())
        .isEqualTo(current.getSpanContext().getSpanId());
  }

  @Test
  void explicitCouchbaseParentTakesPrecedence() {
    CouchbaseTracer couchbaseTracer = new CouchbaseTracer(tracer, true, INTERNAL, false);
    CouchbaseSpan parent = couchbaseTracer.startSpan("parent", null);
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan child = couchbaseTracer.startSpan("child", parent);
      child.end();
    }
    current.end();
    parent.end();

    assertThat(findSpan("child").getParentSpanId())
        .isEqualTo(parent.getSpan().getSpanContext().getSpanId());
  }

  @Test
  void recordsSpanData() {
    CouchbaseSpan span = new CouchbaseTracer(tracer, false, CLIENT, false).startSpan("get", null);
    span.setAttribute("db.system", "couchbase");
    span.setAttribute("db.couchbase.retries", 2L);
    span.addEvent("dispatched", Instant.EPOCH);
    span.setStatus(StatusCode.ERROR);
    span.recordException(new IllegalStateException("failure"));
    span.end();

    SpanData spanData = findSpan("get");
    assertThat(spanData.getKind()).isEqualTo(CLIENT);
    assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(spanData.getAttributes().asMap())
        .containsEntry(stringKey("db.system"), "couchbase")
        .containsEntry(longKey("db.couchbase.retries"), 2L);
    assertThat(spanData.getEvents()).extracting(event -> event.getName()).contains("dispatched");
    assertThat(spanData.getEvents()).extracting(event -> event.getName()).contains("exception");
  }

  private SpanData findSpan(String name) {
    List<SpanData> spans = exporter.getFinishedSpanItems();
    return spans.stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing span: " + name));
  }
}
