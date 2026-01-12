/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.kafka.connect.sink.SinkRecord;

final class KafkaConnectBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<KafkaConnectTask> {

  private final SpanLinksExtractor<SinkRecord> singleRecordLinkExtractor;

  KafkaConnectBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, SinkRecordHeadersGetter.INSTANCE);
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, KafkaConnectTask request) {
    for (SinkRecord record : request.getRecords()) {
      singleRecordLinkExtractor.extract(spanLinks, parentContext, record);
    }
  }
}
