/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Hands exceptions discovered by another instrumentation to the instrumentation that owns the span.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ExceptionRecorder {
  private static final ContextKey<ExceptionRecorder> KEY =
      ContextKey.named("opentelemetry-exception-recorder");

  private final Span span;
  @Nullable private final ExceptionRecorder parent;
  private final ErrorCauseExtractor errorCauseExtractor;
  @Nullable private Recorder recorder;
  @Nullable private Set<Throwable> recorded;

  public static Context storeInContext(
      Context context, ErrorCauseExtractor errorCauseExtractor, Recorder recorder) {
    return context.with(
        KEY,
        new ExceptionRecorder(
            Span.fromContext(context), context.get(KEY), errorCauseExtractor, recorder));
  }

  private ExceptionRecorder(
      Span span,
      @Nullable ExceptionRecorder parent,
      ErrorCauseExtractor errorCauseExtractor,
      Recorder recorder) {
    this.span = span;
    this.parent = parent;
    this.errorCauseExtractor = errorCauseExtractor;
    this.recorder = recorder;
  }

  /**
   * Records an HTTP server exception using the local root span's owner and exception configuration.
   * Without an owner only span events can be recorded; a log's instrumentation scope cannot be
   * recovered from an arbitrary span.
   */
  public static void recordServerException(Context context, Throwable error) {
    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }
    context = context.with(serverSpan);
    ExceptionRecorder owner = findOwner(context);
    if (owner != null) {
      error = owner.errorCauseExtractor.extract(error);
      Recorder recorder = owner.getRecorder(error);
      if (recorder != null) {
        recorder.record(context, error);
      }
    } else if (emitExceptionAsSpanEvents()) {
      serverSpan.recordException(ErrorCauseExtractor.getDefault().extract(error));
    }
  }

  /** Used by Instrumenter after unwrapping its terminal error. */
  public static boolean shouldRecord(Context context, Throwable error) {
    ExceptionRecorder owner = findOwner(context);
    return owner == null || owner.getRecorder(error) != null;
  }

  /** Releases request and exception references when the owning operation finishes. */
  public static void end(Context context) {
    ExceptionRecorder owner = findOwner(context);
    if (owner != null) {
      owner.clear();
    }
  }

  @Nullable
  private static ExceptionRecorder findOwner(Context context) {
    Span span = Span.fromContext(context);
    for (ExceptionRecorder owner = context.get(KEY); owner != null; owner = owner.parent) {
      if (owner.span == span) {
        return owner;
      }
    }
    return null;
  }

  @Nullable
  private synchronized Recorder getRecorder(Throwable error) {
    if (recorder == null) {
      return null;
    }
    if (recorded == null) {
      recorded = Collections.newSetFromMap(new IdentityHashMap<>());
    }
    return recorded.add(error) ? recorder : null;
  }

  private synchronized void clear() {
    recorder = null;
    recorded = null;
  }

  /**
   * Callback into the owning Instrumenter, retaining its request-specific event extraction.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  @FunctionalInterface
  public interface Recorder {
    void record(Context context, Throwable error);
  }
}
