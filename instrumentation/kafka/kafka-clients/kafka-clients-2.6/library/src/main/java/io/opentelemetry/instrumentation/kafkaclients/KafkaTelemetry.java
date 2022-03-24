/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerRecordGetter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaHeadersSetter;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

public final class KafkaTelemetry {
  private static final Logger logger = Logger.getLogger(KafkaTelemetry.class.getName());

  private static final TextMapGetter<ConsumerRecord<?, ?>> GETTER =
      KafkaConsumerRecordGetter.INSTANCE;

  private static final TextMapSetter<Headers> SETTER = KafkaHeadersSetter.INSTANCE;

  private final OpenTelemetry openTelemetry;
  private final Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter;
  private final Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter;

  KafkaTelemetry(
      OpenTelemetry openTelemetry,
      Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter,
      Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter) {
    this.openTelemetry = openTelemetry;
    this.producerInstrumenter = producerInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  /** Returns a new {@link KafkaTelemetry} configured with the given {@link OpenTelemetry}. */
  public static KafkaTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link KafkaTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static KafkaTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new KafkaTelemetryBuilder(openTelemetry);
  }

  private TextMapPropagator propagator() {
    return openTelemetry.getPropagators().getTextMapPropagator();
  }

  /** Returns a decorated {@link Producer} that emits spans for each sent message. */
  public <K, V> Producer<K, V> wrap(Producer<K, V> producer) {
    return new TracingProducer<>(producer, this);
  }

  /** Returns a decorated {@link Consumer} that consumes spans for each received message. */
  public <K, V> Consumer<K, V> wrap(Consumer<K, V> consumer) {
    return new TracingConsumer<>(consumer, this);
  }

  /**
   * Build and inject span into record.
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
        logger.log(WARNING, "failed to inject span context. sending record second time?", t);
      }
    }

    producerInstrumenter.end(current, record, null, null);
  }

  /**
   * Build and inject span into record.
   *
   * @param record the producer record to inject span info.
   * @param callback the producer send callback
   * @return send function's result
   */
  <K, V> Future<RecordMetadata> buildAndInjectSpan(
      ProducerRecord<K, V> record,
      Callback callback,
      BiFunction<ProducerRecord<K, V>, Callback, Future<RecordMetadata>> sendFn) {
    Context parentContext = Context.current();
    if (!producerInstrumenter.shouldStart(parentContext, record)) {
      return sendFn.apply(record, callback);
    }

    Context context = producerInstrumenter.start(parentContext, record);
    try (Scope ignored = context.makeCurrent()) {
      propagator().inject(context, record.headers(), SETTER);
      callback = new ProducerCallback(callback, parentContext, context, record);
      return sendFn.apply(record, callback);
    }
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

  private class ProducerCallback implements Callback {
    private final Callback callback;
    private final Context parentContext;
    private final Context context;
    private final ProducerRecord<?, ?> request;

    public ProducerCallback(
        Callback callback, Context parentContext, Context context, ProducerRecord<?, ?> request) {
      this.callback = callback;
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
      producerInstrumenter.end(context, request, null, exception);

      if (callback != null) {
        try (Scope ignored = parentContext.makeCurrent()) {
          callback.onCompletion(metadata, exception);
        }
      }
    }
  }
}
