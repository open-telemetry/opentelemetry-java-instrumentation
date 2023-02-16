/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.KafkaBatchRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContext;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerContext>
      receiveContextField = VirtualField.find(ConsumerRecords.class, KafkaConsumerContext.class);
  private static final VirtualField<ConsumerRecords<?, ?>, State<KafkaBatchRequest>> stateField =
      VirtualField.find(ConsumerRecords.class, State.class);

  private final Instrumenter<KafkaBatchRequest, Void> batchProcessInstrumenter;
  @Nullable private final BatchInterceptor<K, V> decorated;

  InstrumentedBatchInterceptor(
      Instrumenter<KafkaBatchRequest, Void> batchProcessInstrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    KafkaConsumerContext consumerContext = receiveContextField.get(records);
    Context receiveContext = consumerContext != null ? consumerContext.getContext() : null;
    // use the receive CONSUMER span as parent if it's available
    Context parentContext = receiveContext != null ? receiveContext : Context.current();
    String consumerGroup = consumerContext != null ? consumerContext.getConsumerGroup() : null;
    // XXX
    String clientId = consumerContext != null ? consumerContext.getClientId() : null;

    KafkaBatchRequest batchRequest = new KafkaBatchRequest(records, consumerGroup, clientId);
    if (batchProcessInstrumenter.shouldStart(parentContext, batchRequest)) {
      Context context = batchProcessInstrumenter.start(parentContext, batchRequest);
      Scope scope = context.makeCurrent();
      stateField.set(records, State.create(batchRequest, context, scope));
    }

    return decorated == null ? records : decorated.intercept(records, consumer);
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
    State<KafkaBatchRequest> state = stateField.get(records);
    stateField.set(records, null);
    if (state != null) {
      state.scope().close();
      batchProcessInstrumenter.end(state.context(), state.request(), null, error);
    }
  }
}
