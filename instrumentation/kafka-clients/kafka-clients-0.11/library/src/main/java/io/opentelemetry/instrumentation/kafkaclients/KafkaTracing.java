/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.KafkaHeadersGetter;
import io.opentelemetry.instrumentation.kafka.KafkaHeadersSetter;
import io.opentelemetry.instrumentation.kafka.KafkaSingletons;
import io.opentelemetry.instrumentation.kafka.ReceivedRecords;
import io.opentelemetry.instrumentation.kafka.Timer;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaTracing {

  private static final Logger logger = LoggerFactory.getLogger(KafkaTracing.class);

  private static final TextMapGetter<Headers> GETTER = new KafkaHeadersGetter();

  private static final TextMapSetter<Headers> SETTER = new KafkaHeadersSetter();

  private Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter =
      KafkaSingletons.producerInstrumenter();
  private Instrumenter<ReceivedRecords, Void> consumerReceiveInstrumenter =
      KafkaSingletons.consumerReceiveInstrumenter();
  private Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter =
      KafkaSingletons.consumerProcessInstrumenter();

  KafkaTracing() {}

  public static KafkaTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new KafkaTracingBuilder(openTelemetry);
  }

  private static TextMapPropagator propagator() {
    return GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
  }

  void setProducerInstrumenter(Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter) {
    this.producerInstrumenter = Objects.requireNonNull(producerInstrumenter);
  }

  void setConsumerReceiveInstrumenter(Instrumenter<ReceivedRecords, Void> consumerInstrumenter) {
    this.consumerReceiveInstrumenter = Objects.requireNonNull(consumerInstrumenter);
  }

  void setConsumerProcessInstrumenter(
      Instrumenter<ConsumerRecord<?, ?>, Void> consumerInstrumenter) {
    this.consumerProcessInstrumenter = Objects.requireNonNull(consumerInstrumenter);
  }

  /**
   * Build and inject span into record. Return Runnable handle to end the current span.
   *
   * @param record the producer record to inject span info.
   * @return runnable to close the current span
   */
  public <K, V> Runnable buildAndInjectSpan(ProducerRecord<K, V> record) {
    if (!producerInstrumenter.shouldStart(Context.current(), record)) {
      return () -> {};
    }

    Context current = producerInstrumenter.start(Context.current(), record);
    try (Scope ignored = current.makeCurrent()) {
      try {
        propagator().inject(Context.current(), record.headers(), SETTER);
      } catch (Throwable t) {
        // it can happen if headers are read only (when record is sent second time)
        logger.error("failed to inject span context. sending record second time?", t);
      }
    }

    return () -> producerInstrumenter.end(current, record, null, null);
  }

  public <K, V> void buildAndFinishSpan(ConsumerRecords<K, V> records) {
    ReceivedRecords receivedRecords = ReceivedRecords.create(records, Timer.start());

    if (!consumerReceiveInstrumenter.shouldStart(Context.current(), receivedRecords)) {
      return;
    }

    Context context = consumerReceiveInstrumenter.start(Context.current(), receivedRecords);
    try (Scope ignored = context.makeCurrent()) {
      for (ConsumerRecord<K, V> record : records) {
        buildAndFinishChildSpan(record);
      }
      consumerReceiveInstrumenter.end(context, receivedRecords, null, null);
    } catch (RuntimeException e) {
      consumerReceiveInstrumenter.end(context, receivedRecords, null, e);
    }
  }

  private <K, V> void buildAndFinishChildSpan(ConsumerRecord<K, V> record) {
    Context linkedContext = propagator().extract(Context.current(), record.headers(), GETTER);
    Context.current().with(Span.fromContext(linkedContext));

    if (!consumerProcessInstrumenter.shouldStart(Context.current(), record)) {
      return;
    }

    Context current = consumerProcessInstrumenter.start(Context.current(), record);
    consumerProcessInstrumenter.end(current, record, null, null);

    // Inject created span context into record headers for extraction by client to continue span
    // chain
    propagator().inject(current, record.headers(), SETTER); // TODO -- OK?
  }
}
