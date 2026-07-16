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
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

class RocketMqInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

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

    InstrumenterBuilder<Void, Void> batchReceiveInstrumenterBuilder =
        Instrumenter.<Void, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnReceive)
            .addAttributesExtractor(constant(MESSAGING_SYSTEM, "rocketmq"));
    if (emitOldMessagingSemconv()) {
      batchReceiveInstrumenterBuilder.addAttributesExtractor(
          constant(MESSAGING_OPERATION, "receive"));
    }
    if (emitStableMessagingSemconv()) {
      batchReceiveInstrumenterBuilder
          .addAttributesExtractor(constant(MESSAGING_OPERATION_NAME, "receive"))
          .addAttributesExtractor(constant(MESSAGING_OPERATION_TYPE, "receive"));
    }

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(
            openTelemetry, capturedHeaders, captureExperimentalSpanAttributes, false),
        createProcessInstrumenter(
            openTelemetry, capturedHeaders, captureExperimentalSpanAttributes, true),
        batchReceiveInstrumenterBuilder.buildInstrumenter(
          MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE)));
  }

  private static Instrumenter<MessageExt, Void> createProcessInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes,
      boolean batch) {

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<MessageExt, Void> builder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operationType));

    builder.addAttributesExtractor(
        buildMessagingAttributesExtractor(getter, operationType, capturedHeaders));
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new RocketMqConsumerExperimentalAttributeExtractor());
    }
    setMessagingProcessExceptionEventExtractor(builder);

    if (batch) {
      SpanLinksExtractor<MessageExt> spanLinksExtractor =
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(), new TextMapExtractAdapter());

      return builder
          .addSpanLinksExtractor(spanLinksExtractor)
          .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(new TextMapExtractAdapter());
    }
  }

  private static <T> AttributesExtractor<T, Void> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, Void> getter,
      MessagingOperationType operationType,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operationType)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }

  private static String spanNameOnReceive(Void unused) {
    return emitStableMessagingSemconv() ? "receive multiple_sources" : "multiple_sources receive";
  }

  private RocketMqInstrumenterFactory() {}
}
