/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class TelemetryDataUtilTest {

  @Test
  void testTraceOrderPreserved() {
    List<SpanData> spans = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      spans.add(
          TestSpanData.builder()
              .setName("trace" + i)
              .setSpanContext(
                  SpanContext.create(
                      TraceId.fromLongs(0, i),
                      SpanId.fromLong(i),
                      TraceFlags.getDefault(),
                      TraceState.getDefault()))
              .setKind(SpanKind.CLIENT)
              .setStatus(StatusData.unset())
              .setHasEnded(true)
              .setStartEpochNanos(1678338770194000000L)
              .setEndEpochNanos(1678338770196419884L)
              .build());
    }

    List<Consumer<TraceAssert>> asserts = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      String spanName = "trace" + i;
      asserts.add(trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName(spanName)));
    }

    List<List<SpanData>> result = TelemetryDataUtil.groupTraces(spans);
    assertThat(result).hasTracesSatisfyingExactly(asserts);
  }

  @Test
  void testSpanOrderPreserved() {
    List<SpanData> spans = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      spans.add(
          TestSpanData.builder()
              .setName("span" + i)
              .setSpanContext(
                  SpanContext.create(
                      TraceId.fromLongs(0, 1),
                      SpanId.fromLong(i),
                      TraceFlags.getDefault(),
                      TraceState.getDefault()))
              .setKind(SpanKind.CLIENT)
              .setStatus(StatusData.unset())
              .setHasEnded(true)
              .setStartEpochNanos(1678338770194000000L)
              .setEndEpochNanos(1678338770196419884L)
              .build());
    }

    List<Consumer<SpanDataAssert>> asserts = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      String spanName = "span" + i;
      asserts.add(span -> span.hasName(spanName));
    }

    List<List<SpanData>> result = TelemetryDataUtil.groupTraces(spans);
    assertThat(result)
        .hasTracesSatisfyingExactly(trace -> trace.hasSpansSatisfyingExactly(asserts));
  }
}
