/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6.VertxKafkaSingletons.batchProcessInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.vertx.core.Handler;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class InstrumentedBatchRecordsHandler<K, V> implements Handler<ConsumerRecords<K, V>> {

  private final VirtualField<ConsumerRecords<K, V>, Context> receiveContextField;
  @Nullable private final Handler<ConsumerRecords<K, V>> delegate;

  public InstrumentedBatchRecordsHandler(
      VirtualField<ConsumerRecords<K, V>, Context> receiveContextField,
      @Nullable Handler<ConsumerRecords<K, V>> delegate) {
    this.receiveContextField = receiveContextField;
    this.delegate = delegate;
  }

  @Override
  public void handle(ConsumerRecords<K, V> records) {
    Context parentContext = getParentContext(records);

    if (!batchProcessInstrumenter().shouldStart(parentContext, records)) {
      callDelegateHandler(records);
      return;
    }

    // the instrumenter iterates over records when adding links, we need to suppress that
    boolean previousWrappingEnabled = KafkaClientsConsumerProcessTracing.setEnabled(false);
    try {
      Context context = batchProcessInstrumenter().start(parentContext, records);
      Throwable error = null;
      try (Scope ignored = context.makeCurrent()) {
        callDelegateHandler(records);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        batchProcessInstrumenter().end(context, records, null, error);
      }
    } finally {
      KafkaClientsConsumerProcessTracing.setEnabled(previousWrappingEnabled);
    }
  }

  private Context getParentContext(ConsumerRecords<K, V> records) {
    Context receiveContext = receiveContextField.get(records);

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  private void callDelegateHandler(ConsumerRecords<K, V> records) {
    if (delegate != null) {
      delegate.handle(records);
    }
  }
}
