/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.test.server.ServerTraceUtils
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

// TODO: convert all usages of this class to the Java TraceUtils one
class TraceUtils {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test")

  static <T> T runUnderServerTrace(String spanName, Callable<T> r) {
    return ServerTraceUtils.runUnderServerTrace(spanName, r)
  }

  static void runUnderTrace(String spanName, ThrowingRunnable<?> r) {
    runUnderTrace(spanName, (Callable<Void>) {
      r.run()
      return null
    })
  }

  static <T> T runUnderTrace(String spanName, Callable<T> r) {
    try {
      Span span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan()

      try {
        def result = span.makeCurrent().withCloseable {
          r.call()
        }
        span.end()
        return result
      } catch (Exception e) {
        span.setStatus(StatusCode.ERROR)
        span.recordException(e instanceof ExecutionException ? e.getCause() : e)
        span.end()
        throw e
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  static void runInternalSpan(String spanName) {
    tracer.spanBuilder(spanName).startSpan().end()
  }

  static <T> T runUnderTraceWithoutExceptionCatch(String spanName, Callable<T> r) {
    Span span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan()

    try {
      return span.makeCurrent().withCloseable {
        r.call()
      }
    } finally {
      span.end()
    }
  }
}
