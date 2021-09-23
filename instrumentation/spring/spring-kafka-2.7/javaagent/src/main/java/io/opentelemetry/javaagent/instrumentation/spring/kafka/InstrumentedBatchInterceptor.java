/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import static io.opentelemetry.javaagent.instrumentation.spring.kafka.SpringKafkaSingletons.processInstrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.kafka.listener.BatchInterceptor;

public final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {
  private final ContextStore<ConsumerRecords<K, V>, SpanContext> receiveSpanContextStore;
  private final ContextStore<ConsumerRecords<K, V>, State<K, V>> stateStore;
  @Nullable private final BatchInterceptor<K, V> decorated;

  public InstrumentedBatchInterceptor(
      ContextStore<ConsumerRecords<K, V>, SpanContext> receiveSpanContextStore,
      ContextStore<ConsumerRecords<K, V>, State<K, V>> stateStore,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.receiveSpanContextStore = receiveSpanContextStore;
    this.stateStore = stateStore;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    Context parentContext = getParentContext(records);

    if (processInstrumenter().shouldStart(parentContext, records)) {
      Context context = processInstrumenter().start(parentContext, records);
      Scope scope = context.makeCurrent();
      stateStore.put(records, State.create(records, context, scope));
    }

    return decorated == null ? records : decorated.intercept(records, consumer);
  }

  private Context getParentContext(ConsumerRecords<K, V> records) {
    Context parentContext = Context.current();
    // use the receive CONSUMER span as parent if it's available
    SpanContext receiveSpanContext = receiveSpanContextStore.get(records);
    if (receiveSpanContext != null) {
      parentContext = parentContext.with(Span.wrap(receiveSpanContext));
    }
    return parentContext;
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
    State<K, V> state = stateStore.get(records);
    stateStore.put(records, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter().end(state.context(), state.request(), null, error);
    }
  }
}
