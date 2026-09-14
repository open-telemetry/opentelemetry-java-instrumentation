/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;

public class KafkaConnectSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";
  private static final String PROCESS_OPERATION_NAME = "process";
  private static final TextMapPropagator propagator =
      GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();
  private static final ContextKey<Long> CONSUMED_MESSAGES_COUNT_KEY =
      ContextKey.named("opentelemetry-kafka-connect-consumed-messages-count");

  private static final Instrumenter<KafkaConnectTask, Void> instrumenter;
  private static final OperationMetrics consumedMessagesMetrics =
      meter -> {
        OperationListener delegate = MessagingConsumerMetrics.getConsumedMessages().create(meter);
        return new OperationListener() {
          @Override
          public Context onStart(Context context, Attributes startAttributes, long startNanos) {
            Long consumedMessagesCount = context.get(CONSUMED_MESSAGES_COUNT_KEY);
            return consumedMessagesCount != null && consumedMessagesCount > 0
                ? delegate.onStart(context, startAttributes, startNanos)
                : context;
          }

          @Override
          public void onEnd(Context context, Attributes endAttributes, long endNanos) {
            Long consumedMessagesCount = context.get(CONSUMED_MESSAGES_COUNT_KEY);
            if (consumedMessagesCount != null && consumedMessagesCount > 0) {
              delegate.onEnd(
                  context,
                  endAttributes.toBuilder()
                      .put(MESSAGING_BATCH_MESSAGE_COUNT, consumedMessagesCount)
                      .build(),
                  endNanos);
            }
          }
        };
      };

  static {
    KafkaConnectBatchProcessSpanLinksExtractor spanLinksExtractor =
        new KafkaConnectBatchProcessSpanLinksExtractor(propagator);

    InstrumenterBuilder<KafkaConnectTask, Void> builder =
        Instrumenter.<KafkaConnectTask, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    new KafkaConnectAttributesGetter(),
                    MessagingOperationType.PROCESS,
                    PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                MessagingAttributesExtractor.create(
                    new KafkaConnectAttributesGetter(),
                    MessagingOperationType.PROCESS,
                    PROCESS_OPERATION_NAME))
            .addAttributesExtractor(new KafkaConnectBatchAttributesExtractor())
            .addSpanLinksExtractor(spanLinksExtractor)
            .addOperationMetrics(MessagingProcessMetrics.get());
    // The worker task usually polls an instrumented KafkaConsumer, whose receive operation already
    // owns the consumed message count for records it delivered. This operation counts the
    // deliveries the receive operation did not own, so every delivery attempt, including failed and
    // redelivered ones, is counted exactly once per put() invocation.
    if (emitStableMessagingSemconv()) {
      builder
          .addContextCustomizer(
              (context, request, startAttributes) ->
                  context.with(CONSUMED_MESSAGES_COUNT_KEY, request.countUnmarkedRecords()))
          .addOperationMetrics(consumedMessagesMetrics);
    }
    setMessagingProcessExceptionEventExtractor(builder);

    instrumenter =
        builder.buildInstrumenter(
            MessagingSpanKindExtractor.create(MessagingOperationType.PROCESS));
  }

  public static Instrumenter<KafkaConnectTask, Void> instrumenter() {
    return instrumenter;
  }

  private KafkaConnectSingletons() {}
}
