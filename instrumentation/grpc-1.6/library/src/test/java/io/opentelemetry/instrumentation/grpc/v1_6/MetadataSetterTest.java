/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class MetadataSetterTest {

  @Test
  void overwriteTracingHeader() {
    Metadata metadata = new Metadata();
    TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
    for (int i = 1; i <= 2; i++) {
      Context context =
          Context.root()
              .with(
                  Span.wrap(
                      SpanContext.create(
                          TraceId.fromLongs(0, i),
                          SpanId.fromLong(i),
                          TraceFlags.getDefault(),
                          TraceState.getDefault())));
      propagator.inject(context, metadata, MetadataSetter.INSTANCE);
    }

    assertThat(metadata.getAll(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)))
        .hasSize(1);

    Context context =
        propagator.extract(
            Context.root(),
            metadata,
            new TextMapGetter<Metadata>() {
              @Override
              public Iterable<String> keys(Metadata metadata) {
                return metadata.keys();
              }

              @Nullable
              @Override
              public String get(@Nullable Metadata metadata, String key) {
                return metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
              }
            });

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.getTraceId()).isEqualTo(TraceId.fromLongs(0, 2));
    assertThat(spanContext.getSpanId()).isEqualTo(SpanId.fromLong(2));
  }
}
