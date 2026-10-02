/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Shared implementation of the advice that instruments {@code emit} and {@code emitDirect} on both
 * the spout and the bolt output collectors.
 */
public final class StormEmitScope {

  private final CallDepth callDepth;
  @Nullable private final StormEmit emit;
  @Nullable private final Context context;
  @Nullable private final Scope scope;

  private StormEmitScope(
      CallDepth callDepth,
      @Nullable StormEmit emit,
      @Nullable Context context,
      @Nullable Scope scope) {
    this.callDepth = callDepth;
    this.emit = emit;
    this.context = context;
    this.scope = scope;
  }

  public static StormEmitScope start(
      CallDepth callDepth, @Nullable String streamId, List<Object> values) {
    if (callDepth.getAndIncrement() > 0 || StormSingletons.isInternalStream(streamId)) {
      return new StormEmitScope(callDepth, null, null, null);
    }

    Context parentContext = Context.current();
    StormEmit emit = new StormEmit(streamId, values);
    Instrumenter<StormEmit, Void> instrumenter = StormSingletons.producerInstrumenter();
    if (!instrumenter.shouldStart(parentContext, emit)) {
      return new StormEmitScope(callDepth, null, null, null);
    }

    Context context = instrumenter.start(parentContext, emit);
    Scope scope = context.makeCurrent();
    // the tuple is created further down the emit call, while this context is current
    StormSingletons.setPendingEmit(emit);
    return new StormEmitScope(callDepth, emit, context, scope);
  }

  public void end(@Nullable Throwable throwable) {
    if (callDepth.decrementAndGet() > 0) {
      return;
    }
    if (scope == null) {
      return;
    }

    StormSingletons.clearPendingEmit();
    scope.close();
    StormSingletons.producerInstrumenter().end(context, emit, null, throwable);
  }
}
