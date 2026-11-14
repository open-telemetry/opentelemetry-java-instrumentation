/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

final class SpringAwsSqsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-cloud-aws-3.0";
  private static final String PROCESS_OPERATION_NAME = "process";

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      stringKey("messaging.message.id");
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      longKey("messaging.batch.message_count");
  private static final ContextKey<Long> CONSUMED_MESSAGES_COUNT_KEY =
      ContextKey.named("opentelemetry-spring-cloud-aws-sqs-consumed-messages-count");
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

  private static final Instrumenter<SpringAwsSqsBatchProcessRequest, Void> batchProcessInstrumenter;

  static {
    SpringAwsSqsBatchProcessAttributesGetter getter =
        new SpringAwsSqsBatchProcessAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<SpringAwsSqsBatchProcessRequest, Void> builder =
        Instrumenter.<SpringAwsSqsBatchProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operationType, PROCESS_OPERATION_NAME)
                    .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build())
            // a batch has no single creation context to use as a parent, so the creation context
            // of every message is linked instead
            .addSpanLinksExtractor(
                (spanLinks, parentContext, request) -> {
                  for (SqsMessage message : request.getSqsMessages()) {
                    spanLinks.addLink(
                        Span.fromContext(message.getCreationContext()).getSpanContext(),
                        Attributes.of(MESSAGING_MESSAGE_ID, message.getMessageId()));
                  }
                })
            .addOperationMetrics(MessagingProcessMetrics.get());
    // without receive telemetry, deliveries are counted by the process operation, like the sdk does
    // for single messages; a delivery is only counted the first time it is processed
    if (!ExperimentalConfig.get().messagingReceiveInstrumentationEnabled()) {
      builder
          .addContextCustomizer(
              (context, request, startAttributes) ->
                  context.with(CONSUMED_MESSAGES_COUNT_KEY, claimDeliveries(request)))
          .addOperationMetrics(consumedMessagesMetrics);
    }
    setMessagingProcessExceptionEventExtractor(builder);
    batchProcessInstrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static long claimDeliveries(SpringAwsSqsBatchProcessRequest request) {
    long claimed = 0;
    for (SqsMessage message : request.getSqsMessages()) {
      if (message.claimDelivery()) {
        claimed++;
      }
    }
    return claimed;
  }

  static Instrumenter<SpringAwsSqsBatchProcessRequest, Void> batchProcessInstrumenter() {
    return batchProcessInstrumenter;
  }

  private SpringAwsSqsSingletons() {}
}
