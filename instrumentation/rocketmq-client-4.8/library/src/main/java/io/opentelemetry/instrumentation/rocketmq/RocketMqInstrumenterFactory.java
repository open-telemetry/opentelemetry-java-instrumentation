/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

class RocketMqInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";

  private static final RockerMqProducerAttributeExtractor producerAttributesExtractor =
      new RockerMqProducerAttributeExtractor();
  private static final RockerMqProducerExperimentalAttributeExtractor
      experimentalProducerAttributesExtractor =
          new RockerMqProducerExperimentalAttributeExtractor();

  public static final RockerMqConsumerAttributeExtractor consumerAttributesExtractor =
      new RockerMqConsumerAttributeExtractor();
  public static final RockerMqConsumerExperimentalAttributeExtractor
      experimentalConsumerAttributesExtractor =
          new RockerMqConsumerExperimentalAttributeExtractor();

  static Instrumenter<SendMessageContext, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {

    InstrumenterBuilder<SendMessageContext, Void> instrumenterBuilder =
        Instrumenter.<SendMessageContext, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnProduce)
            .addAttributesExtractor(producerAttributesExtractor);
    if (captureExperimentalSpanAttributes) {
      instrumenterBuilder.addAttributesExtractor(experimentalProducerAttributesExtractor);
    }

    if (propagationEnabled) {
      return instrumenterBuilder.newProducerInstrumenter(MapSetter.INSTANCE);
    } else {
      return instrumenterBuilder.newInstrumenter(SpanKindExtractor.alwaysProducer());
    }
  }

  static RocketMqConsumerInstrumenter createConsumerInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {

    InstrumenterBuilder<Void, Void> batchReceiveInstrumenterBuilder =
        Instrumenter.<Void, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnReceive)
            .addAttributesExtractor(constant(MESSAGING_SYSTEM, "rocketmq"))
            .addAttributesExtractor(constant(MESSAGING_OPERATION, "receive"));

    return new RocketMqConsumerInstrumenter(
        createProcessInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes, propagationEnabled, false),
        createProcessInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes, propagationEnabled, true),
        batchReceiveInstrumenterBuilder.newInstrumenter(SpanKindExtractor.alwaysConsumer()));
  }

  private static Instrumenter<MessageExt, Void> createProcessInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled,
      boolean batch) {

    InstrumenterBuilder<MessageExt, Void> builder =
        Instrumenter.builder(
            openTelemetry, INSTRUMENTATION_NAME, RocketMqInstrumenterFactory::spanNameOnConsume);

    builder.addAttributesExtractor(consumerAttributesExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(experimentalConsumerAttributesExtractor);
    }

    if (!propagationEnabled) {
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    }

    if (batch) {
      SpanLinksExtractor<MessageExt> spanLinksExtractor =
          SpanLinksExtractor.fromUpstreamRequest(
              openTelemetry.getPropagators(), TextMapExtractAdapter.GETTER);

      return builder
          .addSpanLinksExtractor(spanLinksExtractor)
          .newInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.newConsumerInstrumenter(TextMapExtractAdapter.GETTER);
    }
  }

  private static String spanNameOnReceive(Void unused) {
    return "multiple_sources receive";
  }

  private static String spanNameOnProduce(SendMessageContext request) {
    return request.getMessage().getTopic() + " send";
  }

  private static String spanNameOnConsume(MessageExt msg) {
    return msg.getTopic() + " process";
  }
}
