/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

@SuppressWarnings("ThreadLocalUsage") // invocation state and retry tracking
final class InstrumentedBatchInterceptor<K, V> implements BatchInterceptor<K, V> {

  private static final ThreadLocal<WeakReference<ConsumerRecords<?, ?>>> lastProcessed =
      new ThreadLocal<>();

  private final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;
  @Nullable private final BatchInterceptor<K, V> decorated;
  private final ThreadLocal<ProcessingInvocation<KafkaReceiveRequest>> currentInvocation =
      new ThreadLocal<>();
  private final ThreadLocal<ThreadState> currentThreadState = new ThreadLocal<>();
  private final ThreadLocal<Boolean> nextSubBatch = new ThreadLocal<>();

  InstrumentedBatchInterceptor(
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    if (Boolean.TRUE.equals(nextSubBatch.get())) {
      nextSubBatch.remove();
      currentThreadState.set(new ThreadState(currentThreadState.get(), currentInvocation.get()));
    }
    Context parentContext = getParentContext(records);

    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumer);
    Context context = null;
    if (batchProcessInstrumenter.shouldStart(parentContext, request) && !skipProcessing(records)) {
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

  private static boolean skipProcessing(ConsumerRecords<?, ?> records) {
    WeakReference<ConsumerRecords<?, ?>> reference = lastProcessed.get();
    return reference != null && reference.get() == records;
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
      pop(invocation);
    }
    if (invocation.scope != null && invocation.context == Context.current()) {
      invocation.scope.close();
    }
    if (invocation.context != null) {
      batchProcessInstrumenter.end(invocation.context, invocation.request, null, error);
      lastProcessed.set(new WeakReference<>(invocation.request.getRecords()));
    }
  }

  private void pop(ProcessingInvocation<KafkaReceiveRequest> invocation) {
    if (invocation.previous == null) {
      currentInvocation.remove();
    } else {
      currentInvocation.set(invocation.previous);
    }
  }

  // Spring Kafka 2.8+ calls this before each poll of a batch listener container
  @NoMuzzle // method was added in 2.8.0
  @Override
  public void setupThreadState(Consumer<?, ?> consumer) {
    nextSubBatch.remove();
    currentThreadState.set(new ThreadState(currentThreadState.get(), currentInvocation.get()));
    if (decorated != null) {
      decorated.setupThreadState(consumer);
    }
  }

  // Spring Kafka 2.8+ calls this after each poll iteration, including when Spring skipped the
  // success and failure callbacks (e.g. when the transaction could not be started after an early
  // intercept), so end invocations that were started since setupThreadState
  @NoMuzzle // method was added in 2.8.0
  @Override
  public void clearThreadState(Consumer<?, ?> consumer) {
    ThreadState threadState = currentThreadState.get();
    try {
      if (decorated != null) {
        // Close batch scopes above the delegate's setup scope, but stop if the delegate
        // still has a scope open above ours from intercept().
        if (threadState != null) {
          endUnfinishedInvocations(threadState, true);
        }
        decorated.clearThreadState(consumer);
      }
    } finally {
      if (threadState != null) {
        endUnfinishedInvocations(threadState, false);
        if (threadState.previous == null) {
          currentThreadState.remove();
        } else {
          currentThreadState.set(threadState.previous);
        }
        // A physical poll can produce multiple partition sub-batches without another setup call.
        nextSubBatch.set(true);
      }
    }
  }

  private void endUnfinishedInvocations(ThreadState threadState, boolean onlyCurrentScopes) {
    ProcessingInvocation<KafkaReceiveRequest> invocation;
    while ((invocation = currentInvocation.get()) != null
        && invocation != threadState.previousInvocation) {
      if (invocation.completed) {
        if (invocation.scope != null && invocation.context != Context.current()) {
          break;
        }
        pop(invocation);
        if (invocation.scope != null) {
          invocation.scope.close();
        }
      } else {
        if (onlyCurrentScopes
            && invocation.scope != null
            && invocation.context != Context.current()) {
          break;
        }
        end(invocation, invocation.error);
      }
    }
  }

  private static class ThreadState {
    @Nullable final ThreadState previous;
    @Nullable final ProcessingInvocation<KafkaReceiveRequest> previousInvocation;

    ThreadState(
        @Nullable ThreadState previous,
        @Nullable ProcessingInvocation<KafkaReceiveRequest> previousInvocation) {
      this.previous = previous;
      this.previousInvocation = previousInvocation;
    }
  }
}
