/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerRequest;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.springframework.kafka.listener.RecordInterceptor;

final class InstrumentedRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  private static final VirtualField<ConsumerRecord<?, ?>, KafkaConsumerContext>
      receiveContextField = VirtualField.find(ConsumerRecord.class, KafkaConsumerContext.class);
  private static final VirtualField<ConsumerRecord<?, ?>, State<KafkaConsumerRequest>> stateField =
      VirtualField.find(ConsumerRecord.class, State.class);

  private final Instrumenter<KafkaConsumerRequest, Void> processInstrumenter;
  @Nullable private final RecordInterceptor<K, V> decorated;

  InstrumentedRecordInterceptor(
      Instrumenter<KafkaConsumerRequest, Void> processInstrumenter,
      @Nullable RecordInterceptor<K, V> decorated) {
    this.processInstrumenter = processInstrumenter;
    this.decorated = decorated;
  }

  @NoMuzzle
  @SuppressWarnings(
      "deprecation") // implementing deprecated method (removed in 3.0) for better compatibility
  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record) {
    start(record, null, null);
    return decorated == null ? record : decorated.intercept(record);
  }

  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    start(record, null, getClientId(consumer));
    return decorated == null ? record : decorated.intercept(record, consumer);
  }

  private void start(ConsumerRecord<K, V> record, String consumerGroup, String clientId) {
    KafkaConsumerContext consumerContext = receiveContextField.get(record);
    Context receiveContext = consumerContext != null ? consumerContext.getContext() : null;
    // use the receive CONSUMER span as parent if it's available
    Context parentContext = receiveContext != null ? receiveContext : Context.current();
    /*
    String consumerGroup = consumerContext != null ? consumerContext.getConsumerGroup() : null;
    String clientId = consumerContext != null ? consumerContext.getClientId() : null;
     */

    KafkaConsumerRequest request = new KafkaConsumerRequest(record, consumerGroup, clientId);
    if (processInstrumenter.shouldStart(parentContext, request)) {
      Context context = processInstrumenter.start(parentContext, request);
      Scope scope = context.makeCurrent();
      stateField.set(record, State.create(request, context, scope));
    }
  }

  private static <K, V> String getClientId(Consumer<K, V> consumer) {
    Map<MetricName, ? extends Metric> metrics = consumer.metrics();
    Iterator<MetricName> metricIterator = metrics.keySet().iterator();
    return metricIterator.hasNext() ? metricIterator.next().tags().get("client-id") : null;
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
    State<KafkaConsumerRequest> state = stateField.get(record);
    stateField.set(record, null);
    if (state != null) {
      state.scope().close();
      processInstrumenter.end(state.context(), state.request(), null, error);
    }
  }
}
