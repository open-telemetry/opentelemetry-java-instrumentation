/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import org.apache.kafka.connect.sink.SinkRecord;

final class KafkaConnectBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<KafkaConnectTask> {

  private final TextMapPropagator propagator;
  private final SinkRecordHeadersGetter recordGetter;

  KafkaConnectBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
    this.recordGetter = new SinkRecordHeadersGetter();
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, KafkaConnectTask request) {
    KafkaConnectBatchRecordAttributes attributes = request.getBatchRecordAttributes();
    for (SinkRecord record : request.getRecords()) {
      Context extracted = propagator.extract(Context.root(), record, recordGetter);
      spanLinks.addLink(
          Span.fromContext(extracted).getSpanContext(), attributes.getLinkAttributes(record));
    }
  }
}
