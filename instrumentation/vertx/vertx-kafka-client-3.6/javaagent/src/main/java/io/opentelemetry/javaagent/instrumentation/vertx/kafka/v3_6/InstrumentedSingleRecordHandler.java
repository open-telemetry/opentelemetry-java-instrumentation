/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6.VertxKafkaSingletons.processInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.ConsumerAndRecord;
import io.vertx.core.Handler;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class InstrumentedSingleRecordHandler<K, V> implements Handler<ConsumerRecord<K, V>> {

  private final VirtualField<ConsumerRecord<K, V>, Context> receiveContextField;
  private final Consumer<K, V> kafkaConsumer;
  @Nullable private final Handler<ConsumerRecord<K, V>> delegate;

  public InstrumentedSingleRecordHandler(
      VirtualField<ConsumerRecord<K, V>, Context> receiveContextField,
      Consumer<K, V> kafkaConsumer,
      @Nullable Handler<ConsumerRecord<K, V>> delegate) {
    this.receiveContextField = receiveContextField;
    this.kafkaConsumer = kafkaConsumer;
    this.delegate = delegate;
  }

  @Override
  public void handle(ConsumerRecord<K, V> record) {
    Context parentContext = getParentContext(record);
    ConsumerAndRecord<ConsumerRecord<?, ?>> request =
        ConsumerAndRecord.create(kafkaConsumer, record);

    if (!processInstrumenter().shouldStart(parentContext, request)) {
      callDelegateHandler(record);
      return;
    }

    Context context = processInstrumenter().start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      callDelegateHandler(record);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      processInstrumenter().end(context, request, null, error);
    }
  }

  private Context getParentContext(ConsumerRecord<K, V> record) {
    Context receiveContext = receiveContextField.get(record);

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  private void callDelegateHandler(ConsumerRecord<K, V> record) {
    if (delegate != null) {
      delegate.handle(record);
    }
  }
}
