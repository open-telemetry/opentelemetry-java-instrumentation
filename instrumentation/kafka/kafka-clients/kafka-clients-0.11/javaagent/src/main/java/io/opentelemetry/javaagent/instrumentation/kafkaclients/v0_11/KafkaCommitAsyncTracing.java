/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerCommitInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaCommitRequest;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;

public class KafkaCommitAsyncTracing {

  private static final ContextKey<TracingState> TRACING_STATE =
      ContextKey.named(KafkaCommitAsyncTracing.class.getName());
  private static final ContextKey<CallbackState> CALLBACK_STATE =
      ContextKey.named(KafkaCommitAsyncTracing.class.getName() + ".callbackState");

  public static AdviceScope start(
      @Nullable Object offsets, @Nullable OffsetCommitCallback callback) {
    CallDepth callDepth = CallDepth.forClass(KafkaCommitAsyncTracing.class);
    int callDepthValue = callDepth.getAndIncrement();
    CallbackState callbackState = Context.current().get(CALLBACK_STATE);
    int callbackCommitDepth = callbackState == null ? -1 : callbackState.enterCommit();
    boolean adviceScopeCreated = false;
    try {
      if (callDepthValue > 0 && callbackCommitDepth != 0) {
        AdviceScope adviceScope =
            join(callDepth, callback, callbackCommitDepth < 0 ? null : callbackState);
        adviceScopeCreated = true;
        return adviceScope;
      }

      KafkaCommitRequest request = KafkaCommitRequest.create(offsets);
      Context parentContext = Context.current();
      if (callbackCommitDepth != 0
          && !consumerCommitInstrumenter().shouldStart(parentContext, request)) {
        AdviceScope adviceScope =
            new AdviceScope(
                callDepth, null, null, callback, callbackCommitDepth < 0 ? null : callbackState);
        adviceScopeCreated = true;
        return adviceScope;
      }

      Context context = consumerCommitInstrumenter().start(parentContext, request);
      TracingState tracingState = new TracingState(parentContext, context, request);
      Scope scope = context.with(TRACING_STATE, tracingState).makeCurrent();
      AdviceScope adviceScope =
          new AdviceScope(
              callDepth,
              scope,
              tracingState,
              wrapCallback(callback, tracingState),
              callbackCommitDepth < 0 ? null : callbackState);
      adviceScopeCreated = true;
      return adviceScope;
    } finally {
      if (!adviceScopeCreated) {
        callDepth.decrementAndGet();
        if (callbackCommitDepth >= 0) {
          callbackState.exitCommit();
        }
      }
    }
  }

  public static CallbackScope enterCallback(TracingState tracingState) {
    CallbackState callbackState = new CallbackState();
    return new CallbackScope(
        tracingState.parentContext.with(CALLBACK_STATE, callbackState).makeCurrent(),
        callbackState);
  }

  public static AdviceScope join(@Nullable OffsetCommitCallback callback) {
    CallDepth callDepth = CallDepth.forClass(KafkaCommitAsyncTracing.class);
    callDepth.getAndIncrement();
    boolean adviceScopeCreated = false;
    try {
      AdviceScope adviceScope = join(callDepth, callback, null);
      adviceScopeCreated = true;
      return adviceScope;
    } finally {
      if (!adviceScopeCreated) {
        callDepth.decrementAndGet();
      }
    }
  }

  private static AdviceScope join(
      CallDepth callDepth,
      @Nullable OffsetCommitCallback callback,
      @Nullable CallbackState callbackState) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    return new AdviceScope(
        callDepth, null, tracingState, wrapCallback(callback, tracingState), callbackState);
  }

  public static OffsetCommitCallback wrapCallback(OffsetCommitCallback callback) {
    return wrapCallback(callback, Context.current().get(TRACING_STATE));
  }

  @Nullable
  private static OffsetCommitCallback wrapCallback(
      @Nullable OffsetCommitCallback callback, @Nullable TracingState tracingState) {
    if (tracingState == null || callback == null || callback instanceof KafkaCommitCallback) {
      return callback;
    }
    return new KafkaCommitCallback(callback, tracingState);
  }

  @Nullable
  public static OffsetCommitCallback wrapCallbackOrCompletion(
      @Nullable OffsetCommitCallback callback) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    if (tracingState == null || callback instanceof KafkaCommitCallback) {
      return callback;
    }
    return new KafkaCommitCallback(callback, tracingState);
  }

  @Nullable
  public static OffsetCommitCallback useDefaultCallback(
      @Nullable OffsetCommitCallback callback, OffsetCommitCallback defaultCallback) {
    if (callback instanceof KafkaCommitCallback) {
      return ((KafkaCommitCallback) callback).withDefaultCallback(defaultCallback);
    }
    return callback;
  }

  public static void endOnCompletion(@Nullable CompletableFuture<?> future) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    CallbackState callbackState = Context.current().get(CALLBACK_STATE);
    if (tracingState != null
        && future != null
        && (callbackState == null || callbackState.hasCommitInProgress())) {
      future.whenComplete((unused, error) -> tracingState.end(error));
    }
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final Scope scope;
    @Nullable private final TracingState tracingState;
    @Nullable private final OffsetCommitCallback callback;
    @Nullable private final CallbackState callbackState;

    private AdviceScope(
        CallDepth callDepth,
        @Nullable Scope scope,
        @Nullable TracingState tracingState,
        @Nullable OffsetCommitCallback callback,
        @Nullable CallbackState callbackState) {
      this.callDepth = callDepth;
      this.scope = scope;
      this.tracingState = tracingState;
      this.callback = callback;
      this.callbackState = callbackState;
    }

    @Nullable
    public OffsetCommitCallback callback() {
      return callback;
    }

    public void end(@Nullable Throwable error) {
      callDepth.decrementAndGet();
      if (callbackState != null) {
        callbackState.exitCommit();
      }
      if (scope == null) {
        return;
      }

      scope.close();
      if (error != null && tracingState != null) {
        tracingState.end(error);
      }
    }
  }

  private static class CallbackState {
    private boolean active = true;
    private int commitDepth;

    private synchronized int enterCommit() {
      return active ? commitDepth++ : -1;
    }

    private synchronized void exitCommit() {
      commitDepth--;
    }

    private synchronized boolean hasCommitInProgress() {
      return commitDepth > 0;
    }

    private synchronized void deactivate() {
      active = false;
    }
  }

  public static class CallbackScope implements AutoCloseable {
    private final Scope scope;
    private final CallbackState callbackState;

    private CallbackScope(Scope scope, CallbackState callbackState) {
      this.scope = scope;
      this.callbackState = callbackState;
    }

    @Override
    public void close() {
      callbackState.deactivate();
      scope.close();
    }
  }

  public static class TracingState {
    private final Context parentContext;
    private final Context context;
    private final KafkaCommitRequest request;
    private boolean ended;

    private TracingState(Context parentContext, Context context, KafkaCommitRequest request) {
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    public synchronized void end(@Nullable Throwable error) {
      if (ended) {
        return;
      }
      ended = true;
      consumerCommitInstrumenter().end(context, request, null, error);
    }
  }

  private KafkaCommitAsyncTracing() {}
}
