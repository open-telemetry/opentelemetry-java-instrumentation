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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

public final class InstrumentedRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  private final VirtualField<ConsumerRecord<K, V>, Context> receiveContextField;
  private final VirtualField<ConsumerRecord<K, V>, State<ConsumerRecord<K, V>>> stateField;
  @Nullable private final RecordInterceptor<K, V> decorated;

  public InstrumentedRecordInterceptor(
      VirtualField<ConsumerRecord<K, V>, Context> receiveContextField,
      VirtualField<ConsumerRecord<K, V>, State<ConsumerRecord<K, V>>> stateField,
      @Nullable RecordInterceptor<K, V> decorated) {
    this.receiveContextField = receiveContextField;
    this.stateField = stateField;
    this.decorated = decorated;
  }

  @SuppressWarnings("deprecation") // implementing deprecated method for better compatibility
  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record) {
    start(record);
    return decorated == null ? record : decorated.intercept(record);
  }

  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    start(record);
    return decorated == null ? record : decorated.intercept(record, consumer);
  }

  private void start(ConsumerRecord<K, V> record) {
    Context parentContext = getParentContext(record);

    if (processInstrumenter().shouldStart(parentContext, record)) {
      Context context = processInstrumenter().start(parentContext, record);
      Scope scope = context.makeCurrent();
      stateField.set(record, State.create(record, context, scope));
    }
  }

  private Context getParentContext(ConsumerRecord<K, V> records) {
    Context receiveContext = receiveContextField.get(records);

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  @Override
  public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    end(record, null);
    if (decorated != null) {
      decorated.success(record, consumer);
    }
  }

  @Override
  public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
    end(record, exception);
    if (decorated != null) {
      decorated.failure(record, exception, consumer);
    }
  }

  private void end(ConsumerRecord<K, V> record, @Nullable Throwable error) {
    State<ConsumerRecord<K, V>> state = stateField.get(record);
    stateField.set(record, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter().end(state.context(), state.request(), null, error);
    }
  }
}
