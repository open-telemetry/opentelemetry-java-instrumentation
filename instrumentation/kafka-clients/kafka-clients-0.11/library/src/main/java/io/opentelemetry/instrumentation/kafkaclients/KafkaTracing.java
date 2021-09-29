/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerRecordGetter;
import io.opentelemetry.instrumentation.kafka.KafkaHeadersSetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaTracing {
  private static final Logger logger = LoggerFactory.getLogger(KafkaTracing.class);

  private static final TextMapGetter<ConsumerRecord<?, ?>> GETTER = new KafkaConsumerRecordGetter();

  private static final TextMapSetter<Headers> SETTER = new KafkaHeadersSetter();

  private final OpenTelemetry openTelemetry;
  private final Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter;
  private final Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter;

  KafkaTracing(
      OpenTelemetry openTelemetry,
      Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter,
      Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter) {
    this.openTelemetry = openTelemetry;
    this.producerInstrumenter = producerInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  /** Returns a new {@link KafkaTracing} configured with the given {@link OpenTelemetry}. */
  public static KafkaTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link KafkaTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static KafkaTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new KafkaTracingBuilder(openTelemetry);
  }

  private TextMapPropagator propagator() {
    return openTelemetry.getPropagators().getTextMapPropagator();
  }

  /**
   * Build and inject span into record. Return Runnable handle to end the current span.
   *
   * @param record the producer record to inject span info.
   */
  <K, V> void buildAndInjectSpan(ProducerRecord<K, V> record) {
    Context currentContext = Context.current();

    if (!producerInstrumenter.shouldStart(currentContext, record)) {
      return;
    }

    Context current = producerInstrumenter.start(currentContext, record);
    try (Scope ignored = current.makeCurrent()) {
      try {
        propagator().inject(current, record.headers(), SETTER);
      } catch (Throwable t) {
        // it can happen if headers are read only (when record is sent second time)
        logger.error("failed to inject span context. sending record second time?", t);
      }
    }

    producerInstrumenter.end(current, record, null, null);
  }

  <K, V> void buildAndFinishSpan(ConsumerRecords<K, V> records) {
    Context currentContext = Context.current();
    for (ConsumerRecord<K, V> record : records) {
      Context linkedContext = propagator().extract(currentContext, record, GETTER);
      Context newContext = currentContext.with(Span.fromContext(linkedContext));

      if (!consumerProcessInstrumenter.shouldStart(newContext, record)) {
        continue;
      }

      Context current = consumerProcessInstrumenter.start(newContext, record);
      consumerProcessInstrumenter.end(current, record, null, null);
    }
  }
}
