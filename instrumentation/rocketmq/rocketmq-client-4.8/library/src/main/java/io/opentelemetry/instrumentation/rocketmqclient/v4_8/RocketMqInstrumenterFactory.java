/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.SendMessageContext;

class RocketMqInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_NAMESPACE =
      AttributeKey.stringKey("messaging.rocketmq.namespace");
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");

  static Instrumenter<SendMessageContext, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes) {

    RocketMqProducerAttributeGetter getter = new RocketMqProducerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<SendMessageContext, Void> instrumenterBuilder =
        Instrumenter.<SendMessageContext, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operationType, capturedHeaders));
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
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes) {

    InstrumenterBuilder<RocketMqConsumerRequest, Void> batchReceiveInstrumenterBuilder =
        Instrumenter.<RocketMqConsumerRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnReceive)
            .addAttributesExtractor(constant(MESSAGING_SYSTEM, "rocketmq"));
    if (emitOldMessagingSemconv()) {
      batchReceiveInstrumenterBuilder.addAttributesExtractor(
          constant(MESSAGING_OPERATION, "receive"));
    }
    if (emitStableMessagingSemconv()) {
      batchReceiveInstrumenterBuilder
          .addAttributesExtractor(constant(MESSAGING_OPERATION_NAME, "receive"))
          .addAttributesExtractor(constant(MESSAGING_OPERATION_TYPE, "receive"))
          .addAttributesExtractor(
              new AttributesExtractor<RocketMqConsumerRequest, Void>() {
                @Override
                public void onStart(
                    AttributesBuilder attributes,
                    Context parentContext,
                    RocketMqConsumerRequest request) {
                  attributes.put(MESSAGING_BATCH_MESSAGE_COUNT, request.getBatchSize());
                  attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
                  attributes.put(MESSAGING_DESTINATION_NAME, request.getMessage().getTopic());
                  attributes.put(MESSAGING_ROCKETMQ_NAMESPACE, request.getNamespace());
                }

                @Override
                public void onEnd(
                    AttributesBuilder attributes,
                    Context context,
                    RocketMqConsumerRequest request,
                    @Nullable Void unused,
                    @Nullable Throwable error) {}
              });
    }

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(
            openTelemetry, capturedHeaders, captureExperimentalSpanAttributes, false),
        createProcessInstrumenter(
            openTelemetry, capturedHeaders, captureExperimentalSpanAttributes, true),
        batchReceiveInstrumenterBuilder.buildInstrumenter(
            MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE)));
  }

  private static Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      createProcessInstrumenter(
          OpenTelemetry openTelemetry,
          List<String> capturedHeaders,
          boolean captureExperimentalSpanAttributes,
          boolean batch) {

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<RocketMqConsumerRequest, ConsumeMessageContext> builder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operationType));

    builder.addAttributesExtractor(
        buildMessagingAttributesExtractor(getter, operationType, capturedHeaders));
    if (emitStableMessagingSemconv()) {
      builder.addAttributesExtractor(
          new AttributesExtractor<RocketMqConsumerRequest, ConsumeMessageContext>() {
            @Override
            public void onStart(
                AttributesBuilder attributes,
                Context parentContext,
                RocketMqConsumerRequest request) {
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
          });
    }
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqConsumerExperimentalAttributeExtractor());
    }
    builder.setSpanStatusExtractor(
        (spanStatusBuilder, request, response, error) -> {
          if (response != null && !response.isSuccess()) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
          } else {
            SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
          }
        });
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new TextMapExtractAdapter(),
        batch);
  }

  private static <T, R> AttributesExtractor<T, R> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, R> getter,
      MessagingOperationType operationType,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builderForOperationType(getter, operationType)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }

  private static String spanNameOnReceive(RocketMqConsumerRequest request) {
    return emitStableMessagingSemconv()
        ? "receive " + request.getMessage().getTopic()
        : "multiple_sources receive";
  }

  private RocketMqInstrumenterFactory() {}
}
