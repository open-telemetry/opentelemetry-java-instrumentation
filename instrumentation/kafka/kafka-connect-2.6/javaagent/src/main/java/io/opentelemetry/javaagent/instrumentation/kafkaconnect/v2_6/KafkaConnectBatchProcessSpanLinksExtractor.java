/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.kafka.connect.sink.SinkRecord;

final class KafkaConnectBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<KafkaConnectTask> {

  private final SpanLinksExtractor<SinkRecord> singleRecordLinkExtractor;
  private final TextMapPropagator propagator;
  private final SinkRecordHeadersGetter recordGetter;

  KafkaConnectBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
    this.recordGetter = new SinkRecordHeadersGetter();
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, recordGetter);
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, KafkaConnectTask request) {
    if (!emitStableMessagingSemconv()) {
      for (SinkRecord record : request.getRecords()) {
        singleRecordLinkExtractor.extract(spanLinks, parentContext, record);
      }
      return;
    }

    KafkaConnectBatchRecordAttributes attributes = request.getBatchRecordAttributes();
    for (SinkRecord record : request.getRecords()) {
      Context extracted = propagator.extract(Context.root(), record, recordGetter);
      spanLinks.addLink(
          Span.fromContext(extracted).getSpanContext(), attributes.getLinkAttributes(record));
    }
  }
}
