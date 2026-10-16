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
   * Records an exception using the current span's owner, including its exception log configuration.
   * Without an owner only span events can be recorded; a log's instrumentation scope cannot be
   * recovered from an arbitrary span.
   */
  public static void recordException(Context context, Throwable error) {
    recordException(context, Span.fromContext(context), error);
  }

  /**
   * Preserves an integration's event target when its exception log belongs to another span's owner.
   * The context identifies the owning span; the event span need not be current in that context.
   */
  public static void recordException(Context context, Span eventSpan, Throwable error) {
    ExceptionRecorder owner = findOwner(context);
    if (owner != null) {
      error = owner.errorCauseExtractor.extract(error);
      Recorder recorder = owner.getRecorder(error);
      if (recorder != null) {
        recorder.record(context, eventSpan, error);
      }
    } else if (emitExceptionAsSpanEvents()) {
      eventSpan.recordException(error);
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
    void record(Context context, Span eventSpan, Throwable error);
  }
}
