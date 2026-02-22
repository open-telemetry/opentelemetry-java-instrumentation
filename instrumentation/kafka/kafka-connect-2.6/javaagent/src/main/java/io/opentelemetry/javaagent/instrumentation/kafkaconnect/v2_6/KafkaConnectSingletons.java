/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;

public final class KafkaConnectSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";
  private static final TextMapPropagator PROPAGATOR =
      GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();

  private static final Instrumenter<KafkaConnectTask, Void> INSTRUMENTER;

  static {
    KafkaConnectBatchProcessSpanLinksExtractor spanLinksExtractor =
        new KafkaConnectBatchProcessSpanLinksExtractor(PROPAGATOR);

    InstrumenterBuilder<KafkaConnectTask, Void> builder =
        Instrumenter.<KafkaConnectTask, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    KafkaConnectAttributesGetter.INSTANCE, MessageOperation.PROCESS))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        KafkaConnectAttributesGetter.INSTANCE, MessageOperation.PROCESS)
                    .build())
            .addSpanLinksExtractor(spanLinksExtractor);
    Experimental.setExceptionEventExtractor(builder, MessagingExceptionEventExtractors.process());
    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<KafkaConnectTask, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaConnectSingletons() {}
}
