/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Asserter {
  private final TraceAssert traceAssert;
  private final int expectedSpans;
  private List<SpanDataAssert> sortedSpans;

  public Asserter(TraceAssert traceAssert, int expectedSpans) {
    this.traceAssert = traceAssert;
    this.expectedSpans = expectedSpans;
    traceAssert.hasSize(expectedSpans);
  }

  public void span(int index, Consumer<SpanDataAssert> consumer) {
    spans(index, index, consumer);
  }

  public void spans(int from, int to, Consumer<SpanDataAssert> consumer) {
    for (int i = from; i <= to; i++) {
      accept(consumer, i);
    }
  }

  public void spansWithStep(
      int from, int to, int step, int offset, Consumer<SpanDataAssert> consumer) {
    for (int i = from; i <= to; i += step) {
      accept(consumer, i + offset);
    }
  }

  private void accept(Consumer<SpanDataAssert> consumer, int i) {
    consumer.accept(sortedSpans != null ? sortedSpans.get(i) : assertThat(traceAssert.getSpan(i)));
  }

  public List<SpanData> getAll() {
    List<SpanData> spans = new ArrayList<>();
    for (int i = 0; i < expectedSpans; i++) {
      spans.add(traceAssert.getSpan(i));
    }
    return spans;
  }

  public void setSortedSpans(List<SpanData> spans) {
    sortedSpans =
        spans.stream().map(OpenTelemetryAssertions::assertThat).collect(Collectors.toList());
  }
}
