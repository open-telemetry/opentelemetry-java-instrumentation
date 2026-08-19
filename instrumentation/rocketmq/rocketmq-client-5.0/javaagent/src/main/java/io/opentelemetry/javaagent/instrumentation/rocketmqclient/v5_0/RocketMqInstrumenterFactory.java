/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
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
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.util.List;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

final class RocketMqInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-5.0";

  // messaging.operation.name values, named after the RocketMQ API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String RECEIVE_OPERATION_NAME = "receive";
  private static final String PROCESS_OPERATION_NAME = "process";

  private RocketMqInstrumenterFactory() {}

  public static Instrumenter<PublishingMessageImpl, SendReceiptImpl> createProducerInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers) {
    RocketMqProducerAttributeGetter getter = new RocketMqProducerAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    AttributesExtractor<PublishingMessageImpl, SendReceiptImpl> attributesExtractor =
        buildMessagingAttributesExtractor(getter, operationType, SEND_OPERATION_NAME, headers);

    InstrumenterBuilder<PublishingMessageImpl, SendReceiptImpl> instrumenterBuilder =
        Instrumenter.<PublishingMessageImpl, SendReceiptImpl>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME))
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new RocketMqProducerAttributeExtractor())
            .addOperationMetrics(MessagingProducerMetrics.getForOperationType());
    setMessagingSendExceptionEventExtractor(instrumenterBuilder);
    return instrumenterBuilder.buildProducerInstrumenter(new MessageMapSetter());
  }

  public static Instrumenter<RocketMqReceiveRequest, List<MessageView>>
      createConsumerReceiveInstrumenter(
          OpenTelemetry openTelemetry, IncludeExclude headers, boolean enabled) {
    RocketMqConsumerReceiveAttributeGetter getter = new RocketMqConsumerReceiveAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.RECEIVE;

    AttributesExtractor<RocketMqReceiveRequest, List<MessageView>> attributesExtractor =
        buildMessagingAttributesExtractor(getter, operationType, RECEIVE_OPERATION_NAME, headers);

    InstrumenterBuilder<RocketMqReceiveRequest, List<MessageView>> instrumenterBuilder =
        Instrumenter.<RocketMqReceiveRequest, List<MessageView>>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, RECEIVE_OPERATION_NAME))
            .setEnabled(enabled)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new RocketMqConsumerReceiveAttributeExtractor())
            .addOperationMetrics(MessagingConsumerMetrics.getForOperationType());
    if (emitStableMessagingSemconv()) {
      instrumenterBuilder.addAttributesExtractor(
          new RocketMqReceiveBatchMessageAttributeExtractor());
      instrumenterBuilder.addSpanLinksExtractor(
          new RocketMqReceiveSpanLinksExtractor(
              openTelemetry.getPropagators().getTextMapPropagator()));
    }
    setMessagingReceiveExceptionEventExtractor(instrumenterBuilder);
    return instrumenterBuilder.buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  public static Instrumenter<MessageView, ConsumeResult> createConsumerProcessInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers, boolean receiveInstrumentationEnabled) {
    RocketMqConsumerProcessAttributeGetter getter = new RocketMqConsumerProcessAttributeGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    AttributesExtractor<MessageView, ConsumeResult> attributesExtractor =
        buildMessagingAttributesExtractor(getter, operationType, PROCESS_OPERATION_NAME, headers);

    InstrumenterBuilder<MessageView, ConsumeResult> instrumenterBuilder =
        Instrumenter.<MessageView, ConsumeResult>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new RocketMqConsumerProcessAttributeExtractor())
            .addOperationMetrics(MessagingProcessMetrics.get())
            .setSpanStatusExtractor(
                (spanStatusBuilder, messageView, consumeResult, error) -> {
                  if (consumeResult == ConsumeResult.FAILURE) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, messageView, consumeResult, error);
                  }
                });
    if (!receiveInstrumentationEnabled && emitStableMessagingSemconv()) {
      instrumenterBuilder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    }
    setMessagingProcessExceptionEventExtractor(instrumenterBuilder);

    return MessagingProcessInstrumenterFactory.create(
        instrumenterBuilder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new MessageMapGetter(),
        receiveInstrumentationEnabled);
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
}
