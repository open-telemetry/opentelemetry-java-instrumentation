/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.List;

public final class JmsInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private List<String> capturedHeaders = emptyList();
  private boolean messagingReceiveInstrumentationEnabled = false;

  public JmsInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
  }

  @CanIgnoreReturnValue
  public JmsInstrumenterFactory setCapturedHeaders(List<String> capturedHeaders) {
    this.capturedHeaders = capturedHeaders;
    return this;
  }

  @CanIgnoreReturnValue
  public JmsInstrumenterFactory setMessagingReceiveInstrumentationEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    return this;
  }

  public Instrumenter<MessageWithDestination, Void> buildProducerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    return Instrumenter.<MessageWithDestination, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(getter, operation)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .buildProducerInstrumenter(MessagePropertySetter.INSTANCE);
  }

  public Instrumenter<MessageWithDestination, Void> buildConsumerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.RECEIVE;

    // MessageConsumer does not do context propagation
    return Instrumenter.<MessageWithDestination, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(getter, operation)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .setEnabled(messagingReceiveInstrumentationEnabled)
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                openTelemetry.getPropagators().getTextMapPropagator(),
                MessagePropertyGetter.INSTANCE))
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public Instrumenter<MessageWithDestination, Void> buildListenerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<MessageWithDestination, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(getter, operation)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .buildConsumerInstrumenter(MessagePropertyGetter.INSTANCE);
  }
}
