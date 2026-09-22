/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

@SuppressWarnings("ThreadLocalUsage") // callback state belongs to this interceptor instance
final class InstrumentedRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  private final Instrumenter<KafkaProcessRequest, Void> processInstrumenter;
  @Nullable private final RecordInterceptor<K, V> decorated;
  private final ThreadLocal<ProcessingInvocation<KafkaProcessRequest>> currentInvocation =
      new ThreadLocal<>();
  private final ThreadLocal<ThreadState> currentThreadState = new ThreadLocal<>();

  InstrumentedRecordInterceptor(
      Instrumenter<KafkaProcessRequest, Void> processInstrumenter,
      @Nullable RecordInterceptor<K, V> decorated) {
    this.processInstrumenter = processInstrumenter;
    this.decorated = decorated;
  }

  @NoMuzzle
  @SuppressWarnings(
      "deprecation") // implementing deprecated method (removed in 3.0) for better compatibility
  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record) {
    ProcessingInvocation<KafkaProcessRequest> invocation = start(record, null);
    try {
      ConsumerRecord<K, V> result = decorated == null ? record : decorated.intercept(record);
      if (result == null) {
        end(invocation, null);
      } else if (result != record) {
        KafkaConsumerContextUtil.copy(record, result);
      }
      return result;
    } catch (Throwable t) {
      end(invocation, t);
      throw t;
    }
  }

  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaProcessRequest> invocation = start(record, consumer);
    try {
      ConsumerRecord<K, V> result =
          decorated == null ? record : decorated.intercept(record, consumer);
      if (result == null) {
        end(invocation, null);
      } else if (result != record) {
        KafkaConsumerContextUtil.copy(record, result);
      }
      return result;
    } catch (Throwable t) {
      end(invocation, t);
      throw t;
    }
  }

  private ProcessingInvocation<KafkaProcessRequest> start(
      ConsumerRecord<K, V> record, @Nullable Consumer<K, V> consumer) {
    Context parentContext = getParentContext(record);

    KafkaProcessRequest request = KafkaProcessRequest.create(record, consumer);
    Context context = null;
    if (!InstrumentationUtil.shouldSuppressInstrumentation(Context.current())
        && processInstrumenter.shouldStart(parentContext, request)) {
      context = processInstrumenter.start(parentContext, request);
    }
    ProcessingInvocation<KafkaProcessRequest> invocation =
        new ProcessingInvocation<>(request, context, currentInvocation.get());
    currentInvocation.set(invocation);
    return invocation;
  }

  private static Context getParentContext(ConsumerRecord<?, ?> record) {
    KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(record);
    Context receiveContext = consumerContext.getContext();

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  @Override
  public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaProcessRequest> invocation = currentInvocation.get();
    try {
      if (decorated != null) {
        decorated.success(record, consumer);
      }
    } catch (Throwable t) {
      if (invocation != null) {
        invocation.error = t;
      }
      throw t;
    } finally {
      // if thread state is present span is ended in afterRecord
      if (currentThreadState.get() == null) {
        end(invocation, invocation == null ? null : invocation.error);
      }
    }
  }

  @Override
  public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaProcessRequest> invocation = currentInvocation.get();
    try {
      if (decorated != null) {
        decorated.failure(record, exception, consumer);
      }
    } finally {
      // if thread state is present span is ended in afterRecord
      if (currentThreadState.get() == null) {
        end(invocation, exception);
      } else if (invocation != null) {
        invocation.error = exception;
      }
    }
  }

  private void end(
      @Nullable ProcessingInvocation<KafkaProcessRequest> invocation, @Nullable Throwable error) {
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
      processInstrumenter.end(invocation.context, invocation.request, null, error);
    }
  }

  @NoMuzzle // method was added in 2.8.0
  @Override
  public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    ProcessingInvocation<KafkaProcessRequest> invocation = currentInvocation.get();
    end(invocation, invocation == null ? null : invocation.error);
    if (decorated != null) {
      decorated.afterRecord(record, consumer);
    }
  }

  @NoMuzzle // method was added in 2.8.0
  @Override
  public void setupThreadState(Consumer<?, ?> consumer) {
    ThreadState threadState = new ThreadState(currentThreadState.get(), currentInvocation.get());
    currentThreadState.set(threadState);
    if (decorated != null) {
      decorated.setupThreadState(consumer);
    }
  }

  @NoMuzzle // method was added in 2.8.0
  @Override
  public void clearThreadState(Consumer<?, ?> consumer) {
    ThreadState threadState = currentThreadState.get();
    if (threadState != null) {
      ProcessingInvocation<KafkaProcessRequest> invocation;
      while ((invocation = currentInvocation.get()) != null
          && invocation != threadState.previousInvocation) {
        end(invocation, invocation.error);
      }
      if (threadState.previous == null) {
        currentThreadState.remove();
      } else {
        currentThreadState.set(threadState.previous);
      }
    }
    if (decorated != null) {
      decorated.clearThreadState(consumer);
    }
  }

  private static class ThreadState {
    @Nullable final ThreadState previous;
    @Nullable final ProcessingInvocation<KafkaProcessRequest> previousInvocation;

    ThreadState(
        @Nullable ThreadState previous,
        @Nullable ProcessingInvocation<KafkaProcessRequest> previousInvocation) {
      this.previous = previous;
      this.previousInvocation = previousInvocation;
    }
  }
}
