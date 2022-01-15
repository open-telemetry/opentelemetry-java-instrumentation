/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER =
      buildProducerInstrumenter();
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_INSTRUMENTER =
      buildConsumerInstrumenter();
  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER =
      buildListenerInstrumenter();

  private static Instrumenter<MessageWithDestination, Void> buildProducerInstrumenter() {
    JmsMessageAttributesExtractor attributesExtractor =
        new JmsMessageAttributesExtractor(MessageOperation.SEND);
    SpanNameExtractor<MessageWithDestination> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .newProducerInstrumenter(MessagePropertySetter.INSTANCE);
  }

  private static Instrumenter<MessageWithDestination, Void> buildConsumerInstrumenter() {
    JmsMessageAttributesExtractor attributesExtractor =
        new JmsMessageAttributesExtractor(MessageOperation.RECEIVE);
    SpanNameExtractor<MessageWithDestination> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    // MessageConsumer does not do context propagation
    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .setTimeExtractor(new JmsMessageTimeExtractor())
        .setDisabled(ExperimentalConfig.get().suppressMessagingReceiveSpans())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<MessageWithDestination, Void> buildListenerInstrumenter() {
    JmsMessageAttributesExtractor attributesExtractor =
        new JmsMessageAttributesExtractor(MessageOperation.PROCESS);
    SpanNameExtractor<MessageWithDestination> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .newConsumerInstrumenter(MessagePropertyGetter.INSTANCE);
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerInstrumenter() {
    return CONSUMER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private JmsSingletons() {}
}
