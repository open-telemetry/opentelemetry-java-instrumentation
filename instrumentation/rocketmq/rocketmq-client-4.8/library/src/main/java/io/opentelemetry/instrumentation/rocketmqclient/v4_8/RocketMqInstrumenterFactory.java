/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

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
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
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
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
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
            .addOperationMetrics(MessagingProducerMetrics.getForOperationType());
    if (emitStableMessagingSemconv()) {
      instrumenterBuilder.addAttributesExtractor(
          new AttributesExtractor<SendMessageContext, Void>() {
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
          });
    }
    if (captureExperimentalSpanAttributes) {
      instrumenterBuilder.addAttributesExtractor(
          new RocketMqProducerExperimentalAttributeExtractor());
    }
    setMessagingSendExceptionEventExtractor(instrumenterBuilder);

    return instrumenterBuilder.buildProducerInstrumenter(new MapSetter());
  }

  static RocketMqConsumerInstrumenter createConsumerInstrumenter(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes) {

    // the receive span only exists under the old conventions, where it groups the per-message
    // process spans of a batch; under the v1.43 conventions a single process span accounts for the
    // whole batch, and there is no application-initiated receive operation to instrument because
    // the consume hook wraps a push-based callback
    Instrumenter<RocketMqConsumerRequest, Void> batchReceiveInstrumenter =
        Instrumenter.<RocketMqConsumerRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, request -> "multiple_sources receive")
            .addAttributesExtractor(constant(MESSAGING_SYSTEM, "rocketmq"))
            .addAttributesExtractor(constant(MESSAGING_OPERATION, "receive"))
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(openTelemetry, headers, captureExperimentalSpanAttributes, false),
        emitStableMessagingSemconv()
            ? createBatchProcessInstrumenter(
                openTelemetry, headers, captureExperimentalSpanAttributes)
            : createProcessInstrumenter(
                openTelemetry, headers, captureExperimentalSpanAttributes, true),
        batchReceiveInstrumenter);
  }

  // only used under the v1.43 conventions, where a single process span accounts for the whole batch
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
          boolean captureExperimentalSpanAttributes,
          boolean batch) {

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
    if (emitStableMessagingSemconv()) {
      builder.addAttributesExtractor(consumerAttributesExtractor());
    }
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqConsumerExperimentalAttributeExtractor());
    }
    if (emitStableMessagingSemconv()) {
      builder.setSpanStatusExtractor(consumeStatusExtractor());
    }
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new TextMapExtractAdapter(),
        batch);
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

  // rocketmq 4.8 leaves the span status unset when the consumer asks for a redelivery, unless the
  // stable messaging semconv are enabled; rocketmq 5.0 has always reported ERROR for
  // ConsumeResult.FAILURE, so the divergence between the two versions is deliberate
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
