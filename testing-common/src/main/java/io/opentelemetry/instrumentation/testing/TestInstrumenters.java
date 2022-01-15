/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;

/** {@link Instrumenter}s to use when executing test code. */
final class TestInstrumenters {

  private final Instrumenter<String, Void> testInstrumenter;
  private final Instrumenter<String, Void> testClientInstrumenter;
  private final Instrumenter<String, Void> testServerInstrumenter;

  TestInstrumenters(OpenTelemetry openTelemetry) {
    testInstrumenter =
        Instrumenter.<String, Void>builder(openTelemetry, "test", name -> name)
            .newInstrumenter(SpanKindExtractor.alwaysInternal());
    testClientInstrumenter =
        Instrumenter.<String, Void>builder(openTelemetry, "test", name -> name)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
    testServerInstrumenter =
        Instrumenter.<String, Void>builder(openTelemetry, "test", name -> name)
            .newInstrumenter(SpanKindExtractor.alwaysServer());
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return runWithInstrumenter(spanName, testInstrumenter, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithClientSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return runWithInstrumenter(spanName, testClientInstrumenter, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithServerSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return runWithInstrumenter(spanName, testServerInstrumenter, callback);
  }

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback) throws E {
    SpanContext spanContext =
        SpanContext.create(
            "12345678123456781234567812345678",
            "1234567812345678",
            TraceFlags.getDefault(),
            TraceState.getDefault());
    try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
      return callback.get();
    }
  }

  private static <T, E extends Throwable> T runWithInstrumenter(
      String spanName, Instrumenter<String, Void> instrumenter, ThrowingSupplier<T, E> callback)
      throws E {
    Context context = instrumenter.start(Context.current(), spanName);
    Throwable err = null;
    try (Scope ignored = context.makeCurrent()) {
      return callback.get();
    } catch (Throwable t) {
      err = t;
      throw t;
    } finally {
      instrumenter.end(context, spanName, null, err);
    }
  }
}
