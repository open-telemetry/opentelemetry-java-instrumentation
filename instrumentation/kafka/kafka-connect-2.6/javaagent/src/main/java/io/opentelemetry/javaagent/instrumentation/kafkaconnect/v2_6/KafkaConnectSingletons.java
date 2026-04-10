/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class KafkaConnectSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";
  private static final TextMapPropagator propagator =
      GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();

  private static final Instrumenter<KafkaConnectTask, Void> instrumenter;

  static {
    KafkaConnectBatchProcessSpanLinksExtractor spanLinksExtractor =
        new KafkaConnectBatchProcessSpanLinksExtractor(propagator);

    instrumenter =
        Instrumenter.<KafkaConnectTask, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    new KafkaConnectAttributesGetter(), MessageOperation.PROCESS))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        new KafkaConnectAttributesGetter(), MessageOperation.PROCESS)
                    .build())
            .addSpanLinksExtractor(spanLinksExtractor)
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<KafkaConnectTask, Void> instrumenter() {
    return instrumenter;
  }

  private KafkaConnectSingletons() {}
}
