/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import static io.opentelemetry.javaagent.instrumentation.spring.kafka.SpringKafkaSingletons.processInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

public final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {
  private final VirtualField<ConsumerRecords<K, V>, Context> receiveContextVirtualField;
  private final VirtualField<ConsumerRecords<K, V>, State<K, V>> stateStore;
  @Nullable private final BatchInterceptor<K, V> decorated;

  public InstrumentedBatchInterceptor(
      VirtualField<ConsumerRecords<K, V>, Context> receiveContextVirtualField,
      VirtualField<ConsumerRecords<K, V>, State<K, V>> stateStore,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.receiveContextVirtualField = receiveContextVirtualField;
    this.stateStore = stateStore;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    Context parentContext = getParentContext(records);

    if (processInstrumenter().shouldStart(parentContext, records)) {
      Context context = processInstrumenter().start(parentContext, records);
      Scope scope = context.makeCurrent();
      stateStore.set(records, State.create(records, context, scope));
    }

    return decorated == null ? records : decorated.intercept(records, consumer);
  }

  private Context getParentContext(ConsumerRecords<K, V> records) {
    Context receiveContext = receiveContextVirtualField.get(records);

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
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
    stateStore.set(records, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter().end(state.context(), state.request(), null, error);
    }
  }
}
