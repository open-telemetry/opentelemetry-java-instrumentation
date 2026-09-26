/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

@SuppressWarnings("ThreadLocalUsage") // callback state belongs to this interceptor instance
final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {

  private final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;
  @Nullable private final BatchInterceptor<K, V> decorated;
  private final ThreadLocal<ProcessingInvocation<KafkaReceiveRequest>> currentInvocation =
      new ThreadLocal<>();

  InstrumentedBatchInterceptor(
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    Context parentContext = getParentContext(records);

    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumer);
    Context context = null;
    if (batchProcessInstrumenter.shouldStart(parentContext, request)) {
      context = batchProcessInstrumenter.start(parentContext, request);
    }
    ProcessingInvocation<KafkaReceiveRequest> invocation =
        new ProcessingInvocation<>(request, context, currentInvocation.get());
    currentInvocation.set(invocation);
    try {
      ConsumerRecords<K, V> result =
          decorated == null ? records : decorated.intercept(records, consumer);
      if (result == null || context == null) {
        end(invocation, null);
      }
      return result;
    } catch (Throwable t) {
      end(invocation, t);
      throw t;
    }
  }

  private static Context getParentContext(ConsumerRecords<?, ?> records) {
    KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(records);
    Context receiveContext = consumerContext.getContext();

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  @Override
  public void success(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaReceiveRequest> invocation = currentInvocation.get();
    try {
      if (decorated != null) {
        decorated.success(records, consumer);
      }
    } catch (Throwable t) {
      if (invocation != null) {
        invocation.error = t;
      }
      throw t;
    } finally {
      end(invocation, invocation == null ? null : invocation.error);
    }
  }

  @Override
  public void failure(ConsumerRecords<K, V> records, Exception exception, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaReceiveRequest> invocation = currentInvocation.get();
    try {
      if (decorated != null) {
        decorated.failure(records, exception, consumer);
      }
    } finally {
      end(invocation, exception);
    }
  }

  private void end(
      @Nullable ProcessingInvocation<KafkaReceiveRequest> invocation, @Nullable Throwable error) {
    if (invocation == null || invocation.completed) {
      return;
    }
    invocation.completed = true;
    if (currentInvocation.get() == invocation) {
      if (invocation.previous == null) {
        currentInvocation.remove();
      } else {
        currentInvocation.set(invocation.previous);
      }
    }
    if (invocation.scope != null) {
      invocation.scope.close();
    }
    if (invocation.context != null) {
      batchProcessInstrumenter.end(invocation.context, invocation.request, null, error);
    }
  }

  @NoMuzzle // method was added in 2.8.0
  @Override
  public void setupThreadState(Consumer<?, ?> consumer) {
    if (decorated != null) {
      decorated.setupThreadState(consumer);
    }
  }

  @NoMuzzle // method was added in 2.8.0
  @Override
  public void clearThreadState(Consumer<?, ?> consumer) {
    if (decorated != null) {
      decorated.clearThreadState(consumer);
    }
  }
}
