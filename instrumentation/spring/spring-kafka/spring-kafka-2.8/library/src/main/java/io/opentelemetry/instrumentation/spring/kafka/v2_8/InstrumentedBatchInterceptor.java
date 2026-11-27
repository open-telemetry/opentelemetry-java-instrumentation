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
      currentPoll.set(new PollState(poll.previous, currentInvocation.get()));
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

  // Spring Kafka 2.8+ calls this before each poll of a batch listener container
  @NoMuzzle // method was added in 2.8.0
  @Override
  public void setupThreadState(Consumer<?, ?> consumer) {
    PollState poll = currentPoll.get();
    currentPoll.set(
        new PollState(
            poll != null && poll.cleared ? poll.previous : poll, currentInvocation.get()));
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
    PollState poll = currentPoll.get();
    if (poll != null && poll.cleared) {
      poll = poll.previous;
    }
    // The delegate can reenter clearThreadState and change the poll state.
    ProcessingInvocation<KafkaReceiveRequest> checkpoint =
        poll == null ? null : poll.previousInvocation;
    try {
      if (decorated != null) {
        // Close batch scopes above the delegate's setup scope, but stop if the delegate
        // still has a scope open above ours from intercept().
        if (poll != null) {
          endUnfinishedInvocations(checkpoint, true);
        }
        decorated.clearThreadState(consumer);
      }
    } finally {
      if (poll != null) {
        endUnfinishedInvocations(checkpoint, false);
        // A physical poll can produce multiple partition sub-batches without another setup call.
        poll.previousInvocation = null;
        poll.cleared = true;
        currentPoll.set(poll);
      }
    }
  }

  private void endUnfinishedInvocations(
      @Nullable ProcessingInvocation<KafkaReceiveRequest> checkpoint, boolean onlyCurrentScopes) {
    ProcessingInvocation<KafkaReceiveRequest> invocation;
    while ((invocation = currentInvocation.get()) != null && invocation != checkpoint) {
      if ((onlyCurrentScopes && invocation.scope != null && invocation.context != Context.current())
          || !end(invocation, invocation.error)) {
        break;
      }
    }
  }

  private static class PollState {
    @Nullable final PollState previous;
    @Nullable ProcessingInvocation<KafkaReceiveRequest> previousInvocation;
    boolean cleared;

    PollState(
        @Nullable PollState previous,
        @Nullable ProcessingInvocation<KafkaReceiveRequest> previousInvocation) {
      this.previous = previous;
      this.previousInvocation = previousInvocation;
    }
  }
}
