/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
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
  private final ThreadLocal<PollState> currentPoll = new ThreadLocal<>();

  InstrumentedBatchInterceptor(
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.decorated = decorated;
  }

  @Override
  public ConsumerRecords<K, V> intercept(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
    PollState poll = currentPoll.get();
    if (poll != null && poll.cleared) {
      // Partition sub-batches can follow clearThreadState without another physical poll.
      currentPoll.set(new PollState(poll.previous, currentInvocation.get(), consumer));
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

  private boolean end(
      @Nullable ProcessingInvocation<KafkaReceiveRequest> invocation, @Nullable Throwable error) {
    if (invocation == null) {
      return false;
    }
    boolean canClose =
        currentInvocation.get() == invocation
            && (invocation.scope == null || invocation.context == Context.current());
    boolean wasCompleted = invocation.completed;
    invocation.completed = true;
    if (canClose) {
      if (invocation.previous == null) {
        currentInvocation.remove();
      } else {
        currentInvocation.set(invocation.previous);
      }
      if (invocation.scope != null) {
        invocation.scope.close();
      }
    }
    if (!wasCompleted && invocation.context != null) {
      batchProcessInstrumenter.end(invocation.context, invocation.request, null, error);
      lastProcessed.set(new WeakReference<>(invocation.request.getRecords()));
    }
    return canClose;
  }

  @Override
  public void setupThreadState(Consumer<?, ?> consumer) {
    PollState poll = currentPoll.get();
    currentPoll.set(
        new PollState(
            poll != null && poll.cleared ? poll.previous : poll,
            currentInvocation.get(),
            consumer));
    if (decorated != null) {
      decorated.setupThreadState(consumer);
    }
  }

  @Override
  public void clearThreadState(Consumer<?, ?> consumer) {
    PollState poll = currentPoll.get();
    if (poll != null && poll.cleared) {
      poll = poll.previous;
    }
    // A composite may clear a filtered sub-batch without calling this interceptor.
    if (poll != null && (poll.cleared || poll.consumer.get() != consumer)) {
      poll = null;
    }
    ProcessingInvocation<KafkaReceiveRequest> checkpoint =
        poll == null ? null : poll.previousInvocation;
    if (poll != null) {
      // Consume this boundary before invoking application code that can reenter the interceptor.
      poll.cleared = true;
      currentPoll.set(poll);
    }
    try {
      if (poll != null) {
        endUnfinishedInvocations(checkpoint, true);
      }
      if (decorated != null) {
        decorated.clearThreadState(consumer);
      }
    } finally {
      if (poll != null) {
        // An early intercept can be followed by failed transaction startup without either
        // completion callback. A delegate's intercept scope must close before our scope.
        endUnfinishedInvocations(checkpoint, false);
        // A cleared boundary can remain until the next partition or enclosing clear.
        poll.previousInvocation = null;
        currentPoll.set(poll);
      }
    }
  }

  private void endUnfinishedInvocations(
      @Nullable ProcessingInvocation<KafkaReceiveRequest> checkpoint, boolean onlyCurrentScopes) {
    ProcessingInvocation<KafkaReceiveRequest> invocation;
    while ((invocation = currentInvocation.get()) != null && invocation != checkpoint) {
      if (onlyCurrentScopes
          && invocation.scope != null
          && invocation.context != Context.current()) {
        break;
      }
      if (!end(invocation, invocation.error)) {
        break;
      }
    }
  }

  private static class PollState {
    @Nullable final PollState previous;
    @Nullable ProcessingInvocation<KafkaReceiveRequest> previousInvocation;
    final WeakReference<Consumer<?, ?>> consumer;
    boolean cleared;

    PollState(
        @Nullable PollState previous,
        @Nullable ProcessingInvocation<KafkaReceiveRequest> previousInvocation,
        @Nullable Consumer<?, ?> consumer) {
      this.previous = previous;
      this.previousInvocation = previousInvocation;
      this.consumer = new WeakReference<>(consumer);
    }
  }
}
