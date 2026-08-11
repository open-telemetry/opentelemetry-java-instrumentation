/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerCommitInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaCommitRequest;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;

public class KafkaCommitAsyncTracing {

  private static final ContextKey<TracingState> TRACING_STATE =
      ContextKey.named(KafkaCommitAsyncTracing.class.getName());
  private static final VirtualField<Map<?, ?>, TracingState> OFFSET_TRACING_STATE =
      VirtualField.find(Map.class, TracingState.class);

  public static AdviceScope start(
      @Nullable Object offsets, @Nullable OffsetCommitCallback callback) {
    CallDepth callDepth = CallDepth.forClass(KafkaConsumer.class);
    if (callDepth.getAndIncrement() > 0) {
      return join(callDepth, callback);
    }

    KafkaCommitRequest request = KafkaCommitRequest.create(offsets);
    Context parentContext = Context.current();
    if (!consumerCommitInstrumenter().shouldStart(parentContext, request)) {
      return new AdviceScope(callDepth, null, null, callback);
    }

    Context context = consumerCommitInstrumenter().start(parentContext, request);
    TracingState tracingState = new TracingState(context, request);
    Scope scope = context.with(TRACING_STATE, tracingState).makeCurrent();
    return new AdviceScope(callDepth, scope, tracingState, wrapCallback(callback, tracingState));
  }

  public static AdviceScope join(@Nullable OffsetCommitCallback callback) {
    CallDepth callDepth = CallDepth.forClass(KafkaConsumer.class);
    callDepth.getAndIncrement();
    return join(callDepth, callback);
  }

  private static AdviceScope join(CallDepth callDepth, @Nullable OffsetCommitCallback callback) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    return new AdviceScope(callDepth, null, tracingState, wrapCallback(callback, tracingState));
  }

  public static void trackOffsets(Map<?, ?> offsets) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    if (tracingState != null) {
      OFFSET_TRACING_STATE.set(offsets, tracingState);
    }
  }

  public static void endTrackedOffsets(Map<?, ?> offsets, @Nullable Throwable error) {
    TracingState tracingState = OFFSET_TRACING_STATE.get(offsets);
    if (tracingState != null) {
      OFFSET_TRACING_STATE.set(offsets, null);
      tracingState.end(error);
    }
  }

  public static void endOnCompletion(@Nullable CompletableFuture<?> future) {
    TracingState tracingState = Context.current().get(TRACING_STATE);
    if (tracingState != null && future != null) {
      future.whenComplete((unused, error) -> tracingState.end(error));
    }
  }

  @Nullable
  private static OffsetCommitCallback wrapCallback(
      @Nullable OffsetCommitCallback callback, @Nullable TracingState tracingState) {
    if (tracingState == null || callback == null || callback instanceof KafkaCommitCallback) {
      return callback;
    }
    return new KafkaCommitCallback(callback, tracingState);
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final Scope scope;
    @Nullable private final TracingState tracingState;
    @Nullable private final OffsetCommitCallback callback;

    private AdviceScope(
        CallDepth callDepth,
        @Nullable Scope scope,
        @Nullable TracingState tracingState,
        @Nullable OffsetCommitCallback callback) {
      this.callDepth = callDepth;
      this.scope = scope;
      this.tracingState = tracingState;
      this.callback = callback;
    }

    @Nullable
    public OffsetCommitCallback callback() {
      return callback;
    }

    public void end(@Nullable Throwable error) {
      callDepth.decrementAndGet();
      if (scope == null) {
        return;
      }

      scope.close();
      if (error != null && tracingState != null) {
        tracingState.end(error);
      }
    }
  }

  public static class TracingState {
    private final Context context;
    private final KafkaCommitRequest request;
    private boolean ended;

    private TracingState(Context context, KafkaCommitRequest request) {
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
