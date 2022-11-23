/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

final class InstrumentedRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  private static final VirtualField<ConsumerRecord<?, ?>, Context> receiveContextField =
      VirtualField.find(ConsumerRecord.class, Context.class);
  private static final VirtualField<ConsumerRecord<?, ?>, State<ConsumerRecord<?, ?>>> stateField =
      VirtualField.find(ConsumerRecord.class, State.class);
  private static final MethodHandle interceptRecord;

  static {
    MethodHandle interceptRecordHandle;
    try {
      interceptRecordHandle =
          MethodHandles.lookup()
              .findVirtual(
                  RecordInterceptor.class,
                  "intercept",
                  MethodType.methodType(ConsumerRecord.class, ConsumerRecord.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      interceptRecordHandle = null;
    }
    interceptRecord = interceptRecordHandle;
  }

  private final Instrumenter<ConsumerRecord<?, ?>, Void> processInstrumenter;
  @Nullable private final RecordInterceptor<K, V> decorated;

  InstrumentedRecordInterceptor(
      Instrumenter<ConsumerRecord<?, ?>, Void> processInstrumenter,
      @Nullable RecordInterceptor<K, V> decorated) {
    this.processInstrumenter = processInstrumenter;
    this.decorated = decorated;
  }

  @SuppressWarnings({
    "deprecation",
    "unchecked"
  }) // implementing deprecated method (removed in 3.0) for better compatibility
  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record) {
    if (interceptRecord == null) {
      return null;
    }
    start(record);
    if (decorated == null) {
      return null;
    }
    try {
      return (ConsumerRecord<K, V>) interceptRecord.invoke(decorated, record);
    } catch (Throwable e) {
      rethrow(e);
      return null; // unreachable
    }
  }

  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    start(record);
    return decorated == null ? record : decorated.intercept(record, consumer);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void rethrow(Throwable e) throws E {
    throw (E) e;
  }

  private void start(ConsumerRecord<K, V> record) {
    Context parentContext = getParentContext(record);

    if (processInstrumenter.shouldStart(parentContext, record)) {
      Context context = processInstrumenter.start(parentContext, record);
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
    State<ConsumerRecord<?, ?>> state = stateField.get(record);
    stateField.set(record, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter.end(state.context(), state.request(), null, error);
    }
  }
}
