/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER;

  static {
    JmsMessageAttributesExtractor attributesExtractor = new JmsMessageAttributesExtractor();
    SpanNameExtractor<MessageWithDestination> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    OpenTelemetry otel = GlobalOpenTelemetry.get();
    PRODUCER_INSTRUMENTER =
        Instrumenter.<MessageWithDestination, Void>newBuilder(
                otel, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .newProducerInstrumenter(new MessagePropertySetter());
    // MessageConsumer does not do context propagation
    CONSUMER_INSTRUMENTER =
        Instrumenter.<MessageWithDestination, Void>newBuilder(
                otel, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .setTimeExtractors(
                MessageWithDestination::startTime, (request, response, error) -> request.endTime())
            .newInstrumenter(SpanKindExtractor.alwaysConsumer());
    LISTENER_INSTRUMENTER =
        Instrumenter.<MessageWithDestination, Void>newBuilder(
                otel, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .newConsumerInstrumenter(new MessagePropertyGetter());
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
