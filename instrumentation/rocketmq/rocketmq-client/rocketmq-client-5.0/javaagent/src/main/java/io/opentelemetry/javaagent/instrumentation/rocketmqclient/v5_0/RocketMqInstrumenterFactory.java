/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import java.util.List;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

final class RocketMqInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-5.0";

  private RocketMqInstrumenterFactory() {}

  public static Instrumenter<PublishingMessageImpl, SendReceiptImpl> createProducerInstrumenter(
      OpenTelemetry openTelemetry, List<String> capturedHeaders) {

    RocketMqProducerAttributeGetter getter = RocketMqProducerAttributeGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    AttributesExtractor<PublishingMessageImpl, SendReceiptImpl> attributesExtractor =
        buildMessagingAttributesExtractor(getter, operation, capturedHeaders);

    InstrumenterBuilder<PublishingMessageImpl, SendReceiptImpl> instrumenterBuilder =
        Instrumenter.<PublishingMessageImpl, SendReceiptImpl>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(RocketMqProducerAttributeExtractor.INSTANCE)
            .setSpanStatusExtractor(
                (spanStatusBuilder, message, sendReceipt, error) -> {
                  if (null != error) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  }
                });

    return instrumenterBuilder.buildProducerInstrumenter(MapSetter.INSTANCE);
  }

  private static <T, R> MessagingAttributesExtractor<T, R> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, R> getter,
      MessageOperation operation,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
