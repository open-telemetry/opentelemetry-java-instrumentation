/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.ArrayList;
import java.util.Collection;
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
  public JmsInstrumenterFactory setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  @CanIgnoreReturnValue
  public JmsInstrumenterFactory setMessagingReceiveTelemetryEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    return this;
  }

  public Instrumenter<MessageWithDestination, Void> createProducerInstrumenter() {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessageOperation operation = MessageOperation.PUBLISH;

    return Instrumenter.<MessageWithDestination, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(createMessagingAttributesExtractor(operation))
        .buildProducerInstrumenter(new MessagePropertySetter());
  }

  public Instrumenter<MessageWithDestination, Void> createConsumerReceiveInstrumenter() {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessageOperation operation = MessageOperation.RECEIVE;

    InstrumenterBuilder<MessageWithDestination, Void> builder =
        Instrumenter.<MessageWithDestination, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(createMessagingAttributesExtractor(operation));
    if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(), new MessagePropertyGetter()));
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public Instrumenter<MessageWithDestination, Void> createConsumerProcessInstrumenter(
      boolean canHaveReceiveInstrumentation) {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessageOperation operation = MessageOperation.PROCESS;

    InstrumenterBuilder<MessageWithDestination, Void> builder =
        Instrumenter.<MessageWithDestination, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(createMessagingAttributesExtractor(operation));
    if (canHaveReceiveInstrumentation && messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(), new MessagePropertyGetter()));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(new MessagePropertyGetter());
    }
  }

  private AttributesExtractor<MessageWithDestination, Void> createMessagingAttributesExtractor(
      MessageOperation operation) {
    return MessagingAttributesExtractor.builder(new JmsMessageAttributesGetter(), operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
