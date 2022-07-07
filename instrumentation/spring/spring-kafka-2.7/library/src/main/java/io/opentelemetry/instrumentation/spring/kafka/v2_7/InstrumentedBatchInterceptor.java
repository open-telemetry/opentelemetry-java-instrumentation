/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {

  private static final VirtualField<ConsumerRecords<?, ?>, Context> receiveContextField =
      VirtualField.find(ConsumerRecords.class, Context.class);
  private static final VirtualField<ConsumerRecords<?, ?>, State<ConsumerRecords<?, ?>>>
      stateField = VirtualField.find(ConsumerRecords.class, State.class);

  private final Instrumenter<ConsumerRecords<?, ?>, Void> batchProcessInstrumenter;
  @Nullable private final BatchInterceptor<K, V> decorated;

  InstrumentedBatchInterceptor(
      Instrumenter<ConsumerRecords<?, ?>, Void> batchProcessInstrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    Context parentContext = getParentContext(records);

    if (batchProcessInstrumenter.shouldStart(parentContext, records)) {
      Context context = batchProcessInstrumenter.start(parentContext, records);
      Scope scope = context.makeCurrent();
      stateField.set(records, State.create(records, context, scope));
    }

    return decorated == null ? records : decorated.intercept(records, consumer);
  }

  private Context getParentContext(ConsumerRecords<K, V> records) {
    Context receiveContext = receiveContextField.get(records);

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
    State<ConsumerRecords<?, ?>> state = stateField.get(records);
    stateField.set(records, null);
    if (state != null) {
      state.scope().close();
      batchProcessInstrumenter.end(state.context(), state.request(), null, error);
    }
  }
}
