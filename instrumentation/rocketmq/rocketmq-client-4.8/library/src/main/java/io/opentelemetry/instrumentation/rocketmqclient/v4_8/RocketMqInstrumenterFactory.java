/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.SendMessageContext;

class RocketMqInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";

  // messaging.operation.name values, named after the RocketMQ API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String PROCESS_OPERATION_NAME = "process";

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_NAMESPACE =
      AttributeKey.stringKey("messaging.rocketmq.namespace");

  static Instrumenter<SendMessageContext, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes) {

    RocketMqProducerAttributeGetter getter = new RocketMqProducerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<SendMessageContext, Void> instrumenterBuilder =
        Instrumenter.<SendMessageContext, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, SEND_OPERATION_NAME, headers))
            .addOperationMetrics(MessagingProducerMetrics.get());
    instrumenterBuilder.addAttributesExtractor(producerAttributesExtractor());
    if (captureExperimentalSpanAttributes) {
      instrumenterBuilder.addAttributesExtractor(
          new RocketMqProducerExperimentalAttributeExtractor());
    }
    setMessagingSendExceptionEventExtractor(instrumenterBuilder);

    return instrumenterBuilder.buildProducerInstrumenter(new MapSetter());
  }

  static Instrumenter<SendMessageContext, Void> createBatchProducerInstrumenter(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes) {
    RocketMqProducerAttributeGetter getter = new RocketMqProducerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;
    InstrumenterBuilder<SendMessageContext, Void> builder =
        Instrumenter.<SendMessageContext, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, SEND_OPERATION_NAME, headers))
            .addSpanLinksExtractor(new RocketMqBatchSendSpanLinksExtractor())
            .addAttributesExtractor(producerAttributesExtractor())
            .addOperationMetrics(MessagingProducerMetrics.get());
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqProducerExperimentalAttributeExtractor());
    }
    setMessagingSendExceptionEventExtractor(builder);
    return InstrumenterUtil.buildDownstreamInstrumenter(
        builder,
        new MapSetter(),
        MessagingSpanKindExtractor.create(
            operationType,
            request ->
                !RocketMqBatchSendSpanLinksExtractor.allMessagesHaveCreationContext(request)));
  }

  static Instrumenter<SendMessageContext, Void> createMessageCreateInstrumenter(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean batchSendMessageCreationSpansEnabled) {
    RocketMqProducerAttributeGetter getter = new RocketMqProducerAttributeGetter(true);
    MessagingOperationType operationType = MessagingOperationType.CREATE;
    return Instrumenter.<SendMessageContext, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operationType, "create"))
        .setEnabled(batchSendMessageCreationSpansEnabled)
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operationType, "create", headers))
        .addAttributesExtractor(producerAttributesExtractor())
        .buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  private static AttributesExtractor<SendMessageContext, Void> producerAttributesExtractor() {
    return new AttributesExtractor<SendMessageContext, Void>() {
      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, SendMessageContext request) {
        String namespace = RocketMqNamespaceUtil.getNamespace(request);
        attributes.put(MESSAGING_ROCKETMQ_NAMESPACE, namespace == null ? "" : namespace);
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          SendMessageContext request,
          @Nullable Void unused,
          @Nullable Throwable error) {}
    };
  }

  static RocketMqConsumerInstrumenter createConsumerInstrumenter(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes) {

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(openTelemetry, headers, captureExperimentalSpanAttributes),
        createBatchProcessInstrumenter(openTelemetry, headers, captureExperimentalSpanAttributes));
  }

  private static Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      createBatchProcessInstrumenter(
          OpenTelemetry openTelemetry,
          IncludeExclude headers,
          boolean captureExperimentalSpanAttributes) {

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<RocketMqConsumerRequest, ConsumeMessageContext> builder =
        Instrumenter.<RocketMqConsumerRequest, ConsumeMessageContext>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, PROCESS_OPERATION_NAME, headers))
            .addAttributesExtractor(consumerAttributesExtractor())
            .addSpanLinksExtractor(
                new RocketMqBatchProcessSpanLinksExtractor(
                    openTelemetry.getPropagators().getTextMapPropagator(),
                    captureExperimentalSpanAttributes))
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages())
            .setSpanStatusExtractor(consumeStatusExtractor());
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqBatchProcessAttributeExtractor());
    }
    setMessagingProcessExceptionEventExtractor(builder);

    // a batch has no single message creation context that could be adopted as the span's parent,
    // so this instrumenter is built directly instead of going through
    // MessagingProcessInstrumenterFactory
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      createProcessInstrumenter(
          OpenTelemetry openTelemetry,
          IncludeExclude headers,
          boolean captureExperimentalSpanAttributes) {

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<RocketMqConsumerRequest, ConsumeMessageContext> builder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME));

    builder.addAttributesExtractor(
        buildMessagingAttributesExtractor(getter, operationType, PROCESS_OPERATION_NAME, headers));
    builder.addOperationMetrics(MessagingProcessMetrics.get());
    builder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    builder.addAttributesExtractor(consumerAttributesExtractor());
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqConsumerExperimentalAttributeExtractor());
    }
    builder.setSpanStatusExtractor(consumeStatusExtractor());
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new TextMapExtractAdapter());
  }

  private static AttributesExtractor<RocketMqConsumerRequest, ConsumeMessageContext>
      consumerAttributesExtractor() {
    return new AttributesExtractor<RocketMqConsumerRequest, ConsumeMessageContext>() {
      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, RocketMqConsumerRequest request) {
        attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
        attributes.put(MESSAGING_ROCKETMQ_NAMESPACE, request.getNamespace());
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          RocketMqConsumerRequest request,
          @Nullable ConsumeMessageContext response,
          @Nullable Throwable error) {}
    };
  }

  private static SpanStatusExtractor<RocketMqConsumerRequest, ConsumeMessageContext>
      consumeStatusExtractor() {
    return (spanStatusBuilder, request, response, error) -> {
      // the consume return type, and not just the consume status, decides whether the operation
      // failed, so that the span status stays consistent with the reported error.type
      if (RocketMqConsumerAttributeGetter.getErrorType(response) != null) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      } else {
        SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
      }
    };
  }

  private static <T, R> AttributesExtractor<T, R> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, R> getter,
      MessagingOperationType operationType,
      String operationName,
      IncludeExclude headers) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(headers)
        .build();
  }

  private RocketMqInstrumenterFactory() {}
}
