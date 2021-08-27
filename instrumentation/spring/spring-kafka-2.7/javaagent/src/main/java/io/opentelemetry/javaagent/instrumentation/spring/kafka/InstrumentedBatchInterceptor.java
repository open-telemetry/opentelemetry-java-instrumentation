/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import static io.opentelemetry.javaagent.instrumentation.spring.kafka.SpringKafkaSingletons.processInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.spring.kafka.SpringKafkaSingletons.receiveInstrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaConsumerIteratorWrapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.kafka.listener.BatchInterceptor;

public final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {
  private final ContextStore<ConsumerRecords<K, V>, State<K, V>> contextStore;
  @Nullable private final BatchInterceptor<K, V> decorated;

  public InstrumentedBatchInterceptor(
      ContextStore<ConsumerRecords<K, V>, State<K, V>> contextStore,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.contextStore = contextStore;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(
      ConsumerRecords<K, V> consumerRecords, Consumer<K, V> consumer) {

    Context parentContext = Context.current();

    // create spans for all records received in a batch
    List<SpanContext> receiveSpanContexts = traceReceivingRecords(parentContext, consumerRecords);

    // then start a span for processing that links all those receive spans
    BatchRecords<K, V> batchRecords = BatchRecords.create(consumerRecords, receiveSpanContexts);
    if (processInstrumenter().shouldStart(parentContext, batchRecords)) {
      Context context = processInstrumenter().start(parentContext, batchRecords);
      Scope scope = context.makeCurrent();
      contextStore.put(consumerRecords, State.create(batchRecords, context, scope));
    }

    return decorated == null ? consumerRecords : decorated.intercept(consumerRecords, consumer);
  }

  private List<SpanContext> traceReceivingRecords(
      Context parentContext, ConsumerRecords<K, V> records) {
    List<SpanContext> receiveSpanContexts = new ArrayList<>();

    Iterator<ConsumerRecord<K, V>> it = records.iterator();
    // this will forcefully suppress the kafka-clients CONSUMER instrumentation even though there's
    // no current CONSUMER span
    // this instrumentation will create CONSUMER receive spans for each record instead of
    // kafka-clients
    if (it instanceof KafkaConsumerIteratorWrapper) {
      it = ((KafkaConsumerIteratorWrapper<K, V>) it).unwrap();
    }

    while (it.hasNext()) {
      ConsumerRecord<K, V> record = it.next();
      if (receiveInstrumenter().shouldStart(parentContext, record)) {
        Context context = receiveInstrumenter().start(parentContext, record);
        receiveSpanContexts.add(Span.fromContext(context).getSpanContext());
        receiveInstrumenter().end(context, record, null, null);
      }
    }

    return receiveSpanContexts;
  }

  @Override
  public void success(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    end(records, null);
    if (decorated != null) {
      decorated.success(records, consumer);
    }
  }

  @Override
  public void failure(ConsumerRecords<K, V> records, Exception exception, Consumer<K, V> consumer) {
    end(records, exception);
    if (decorated != null) {
      decorated.failure(records, exception, consumer);
    }
  }

  private void end(ConsumerRecords<K, V> records, @Nullable Throwable error) {
    State<K, V> state = contextStore.get(records);
    contextStore.put(records, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter().end(state.context(), state.request(), null, error);
    }
  }
}
