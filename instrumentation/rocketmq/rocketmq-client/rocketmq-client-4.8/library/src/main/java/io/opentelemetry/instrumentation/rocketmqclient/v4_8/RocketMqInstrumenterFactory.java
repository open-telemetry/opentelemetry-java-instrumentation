/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

class RocketMqInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";

  static Instrumenter<SendMessageContext, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {

    RocketMqProducerAttributeGetter getter = RocketMqProducerAttributeGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    InstrumenterBuilder<SendMessageContext, Void> instrumenterBuilder =
        Instrumenter.<SendMessageContext, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operation, capturedHeaders));
    if (captureExperimentalSpanAttributes) {
      instrumenterBuilder.addAttributesExtractor(
          RocketMqProducerExperimentalAttributeExtractor.INSTANCE);
    }

    if (propagationEnabled) {
      return instrumenterBuilder.buildProducerInstrumenter(MapSetter.INSTANCE);
    } else {
      return instrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysProducer());
    }
  }

  static RocketMqConsumerInstrumenter createConsumerInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {

    InstrumenterBuilder<Void, Void> batchReceiveInstrumenterBuilder =
        Instrumenter.<Void, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnReceive)
            .addAttributesExtractor(constant(MESSAGING_SYSTEM, "rocketmq"))
            .addAttributesExtractor(constant(MESSAGING_OPERATION, "receive"));

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(
            openTelemetry,
            capturedHeaders,
            captureExperimentalSpanAttributes,
            propagationEnabled,
            false),
        createProcessInstrumenter(
            openTelemetry,
            capturedHeaders,
            captureExperimentalSpanAttributes,
            propagationEnabled,
            true),
        batchReceiveInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysConsumer()));
  }

  private static Instrumenter<MessageExt, Void> createProcessInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled,
      boolean batch) {

    RocketMqConsumerAttributeGetter getter = RocketMqConsumerAttributeGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    InstrumenterBuilder<MessageExt, Void> builder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation));

    builder.addAttributesExtractor(
        buildMessagingAttributesExtractor(getter, operation, capturedHeaders));
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(RocketMqConsumerExperimentalAttributeExtractor.INSTANCE);
    }

    if (!propagationEnabled) {
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }

    if (batch) {
      SpanLinksExtractor<MessageExt> spanLinksExtractor =
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(),
              TextMapExtractAdapter.INSTANCE);

      return builder
          .addSpanLinksExtractor(spanLinksExtractor)
          .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(TextMapExtractAdapter.INSTANCE);
    }
  }

  private static <T> MessagingAttributesExtractor<T, Void> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, Void> getter,
      MessageOperation operation,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }

  private static String spanNameOnReceive(Void unused) {
    return "multiple_sources receive";
  }

  private RocketMqInstrumenterFactory() {}
}
