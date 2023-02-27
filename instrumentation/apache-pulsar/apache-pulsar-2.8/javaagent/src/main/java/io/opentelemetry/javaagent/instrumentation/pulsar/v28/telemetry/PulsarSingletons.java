/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.instrumentation.pulsar.v28.VirtualFieldStore;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.time.Instant;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

public final class PulsarSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.pulsar-client-2.8";
  private static final OpenTelemetry TELEMETRY = GlobalOpenTelemetry.get();
  private static final TextMapPropagator PROPAGATOR =
      TELEMETRY.getPropagators().getTextMapPropagator();

  private static final SpanNameExtractor<Message<?>> CONSUMER_RECEIVE =
      new MessagingSpanNameExtractor<>(SpanKind.CONSUMER, MessageOperation.RECEIVE);
  private static final SpanNameExtractor<Message<?>> CONSUMER_PROCESS =
      new MessagingSpanNameExtractor<>(SpanKind.CONSUMER, MessageOperation.PROCESS);
  private static final SpanNameExtractor<Message<?>> PRODUCER_SEND =
      new MessagingSpanNameExtractor<>(SpanKind.PRODUCER, MessageOperation.SEND);

  private static final Instrumenter<Message<?>, Attributes> CONSUMER_LISTENER_INSTRUMENTER =
      createConsumerListenerInstrumenter();
  private static final Instrumenter<Message<?>, Attributes> CONSUMER_RECEIVE_INSTRUMENTER =
      createConsumerReceiveInstrumenter();
  private static final Instrumenter<Message<?>, Attributes> PRODUER_INSTRUMENTER =
      createProducerInstrumenter();

  public static Instrumenter<Message<?>, Attributes> consumerListenerInstrumenter() {
    return CONSUMER_LISTENER_INSTRUMENTER;
  }

  public static Instrumenter<Message<?>, Attributes> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<Message<?>, Attributes> producerInstrumenter() {
    return PRODUER_INSTRUMENTER;
  }

  private static Instrumenter<Message<?>, Attributes> createConsumerReceiveInstrumenter() {
    MessagingAttributesGetter<Message<?>, Attributes> getter =
        PulsarMessagingAttributesGetter.INSTANCE;

    return Instrumenter.<Message<?>, Attributes>builder(
            TELEMETRY, INSTRUMENTATION_NAME, CONSUMER_RECEIVE)
        .addAttributesExtractor(ConsumerAttributesExtractor.INSTANCE)
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(getter, MessageOperation.RECEIVE))
        .buildConsumerInstrumenter(MessageTextMapGetter.INSTANCE);
  }

  private static Instrumenter<Message<?>, Attributes> createConsumerListenerInstrumenter() {
    MessagingAttributesGetter<Message<?>, Attributes> getter =
        PulsarMessagingAttributesGetter.INSTANCE;

    return Instrumenter.<Message<?>, Attributes>builder(
            TELEMETRY, INSTRUMENTATION_NAME, CONSUMER_PROCESS)
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(getter, MessageOperation.PROCESS))
        .addAttributesExtractor(ConsumerAttributesExtractor.INSTANCE)
        .buildInstrumenter();
  }

  private static Instrumenter<Message<?>, Attributes> createProducerInstrumenter() {
    MessagingAttributesGetter<Message<?>, Attributes> getter =
        PulsarMessagingAttributesGetter.INSTANCE;

    return Instrumenter.<Message<?>, Attributes>builder(
            TELEMETRY, INSTRUMENTATION_NAME, PRODUCER_SEND)
        .addAttributesExtractor(ProducerAttributesExtractor.INSTANCE)
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, MessageOperation.SEND))
        .buildProducerInstrumenter(MessageTextMapSetter.INSTANCE);
  }

  public static Context startAndEndConsumerReceive(
      Context parent, Message<?> message, long start, Consumer<?> consumer) {
    if (message == null || !CONSUMER_RECEIVE_INSTRUMENTER.shouldStart(parent, message)) {
      return null;
    }

    String brokerUrl = VirtualFieldStore.extract(consumer);
    Attributes attributes = Attributes.of(SemanticAttributes.MESSAGING_URL, brokerUrl);
    // startAndEnd not supports extract trace context from carrier
    // start not supports custom startTime
    // extract trace context by using TEXT_MAP_PROPAGATOR here.
    return InstrumenterUtil.startAndEnd(
        CONSUMER_RECEIVE_INSTRUMENTER,
        PROPAGATOR.extract(parent, message, MessageTextMapGetter.INSTANCE),
        message,
        attributes,
        null,
        Instant.ofEpochMilli(start),
        Instant.now());
  }

  private PulsarSingletons() {}

  static class MessagingSpanNameExtractor<T> implements SpanNameExtractor<T> {
    private final String name;

    private MessagingSpanNameExtractor(SpanKind kind, MessageOperation operation) {
      this.name = kind.name() + "/" + operation.name();
    }

    @Override
    public String extract(T unused) {
      return this.name;
    }
  }
}
