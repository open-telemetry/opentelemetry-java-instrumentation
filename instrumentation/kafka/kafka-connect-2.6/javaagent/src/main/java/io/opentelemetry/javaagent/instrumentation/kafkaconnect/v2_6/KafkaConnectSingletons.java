/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.apache.kafka.connect.sink.SinkRecord;

public final class KafkaConnectSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";
  private static final TextMapPropagator PROPAGATOR =
      GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();

  private static final TextMapGetter<SinkRecord> SINK_RECORD_HEADER_GETTER =
      SinkRecordHeadersGetter.INSTANCE;

  private static final Instrumenter<KafkaConnectTask, Void> INSTRUMENTER;

  static {
    KafkaConnectBatchProcessSpanLinksExtractor spanLinksExtractor = new KafkaConnectBatchProcessSpanLinksExtractor(PROPAGATOR);

    INSTRUMENTER =
        Instrumenter.<KafkaConnectTask, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(KafkaConnectAttributesGetter.INSTANCE, MessageOperation.PROCESS))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(KafkaConnectAttributesGetter.INSTANCE, MessageOperation.PROCESS)
                    .build())
            .addSpanLinksExtractor(spanLinksExtractor)
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<KafkaConnectTask, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static TextMapPropagator propagator() {
    return PROPAGATOR;
  }

  public static TextMapGetter<SinkRecord> sinkRecordHeaderGetter() {
    return SINK_RECORD_HEADER_GETTER;
  }

  private KafkaConnectSingletons() {}
}
