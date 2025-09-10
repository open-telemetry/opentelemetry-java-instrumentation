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

/**
 * Extracts span links from Kafka Connect SinkRecord headers for batch processing scenarios.
 * This ensures that when processing a batch of records that may come from different traces,
 * we create links to all the original trace contexts rather than losing them.
 */
final class KafkaConnectBatchProcessSpanLinksExtractor implements SpanLinksExtractor<KafkaConnectTask> {

  private final SpanLinksExtractor<SinkRecord> singleRecordLinkExtractor;

  KafkaConnectBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, SinkRecordHeadersGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, KafkaConnectTask request) {

    for (SinkRecord record : request.getRecords()) {
      // Create a link to each record's original trace context
      // Using Context.root() to avoid linking to the current span
      singleRecordLinkExtractor.extract(spanLinks, Context.root(), record);
    }
  }
}
