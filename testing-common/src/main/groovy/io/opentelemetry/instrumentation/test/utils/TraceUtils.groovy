/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.test.asserts.AttributesAssert
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.server.ServerTraceUtils
import io.opentelemetry.sdk.trace.data.SpanData

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class TraceUtils {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test")

  static <T> T runUnderServerTrace(final String rootOperationName, final Callable<T> r) {
    return ServerTraceUtils.runUnderServerTrace(rootOperationName, r)
  }

  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    try {
      final Span span = tracer.spanBuilder(rootOperationName).setSpanKind(SpanKind.INTERNAL).startSpan()

      try {
        def result = span.makeCurrent().withCloseable {
          r.call()
        }
        span.end()
        return result
      } catch (final Exception e) {
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

  static basicSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null,
                   @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                   @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name operation
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }

      if (additionAttributesAssert != null) {
        attributes(additionAttributesAssert)
      }
    }
  }

  static <T> T runUnderTraceWithoutExceptionCatch(final String rootOperationName, final Callable<T> r) {
    final Span span = tracer.spanBuilder(rootOperationName).setSpanKind(SpanKind.INTERNAL).startSpan()

    try {
      return span.makeCurrent().withCloseable {
        r.call()
      }
    } finally {
      span.end()
    }
  }
}
