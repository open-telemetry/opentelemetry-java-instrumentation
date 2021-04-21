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
import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.instrumentation.test.asserts.AttributesAssert
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.server.ServerTraceUtils
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

// TODO: convert all usages of this class to the Java TraceUtils one
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

  @WithSpan(value = "parent-client-span", kind = SpanKind.CLIENT)
  static <T> T runUnderParentClientSpan(Callable<T> r) {
    r.call()
  }

  static basicClientSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                         @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    return basicSpanForKind(trace, index, SpanKind.CLIENT, operation, parentSpan, exception, additionAttributesAssert)
  }

  static basicServerSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                         @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    return basicSpanForKind(trace, index, SpanKind.SERVER, operation, parentSpan, exception, additionAttributesAssert)
  }

  // TODO rename to basicInternalSpan
  static basicSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null,
                   @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                   @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    return basicSpanForKind(trace, index, SpanKind.INTERNAL, operation, parentSpan, exception, additionAttributesAssert)
  }

  private static basicSpanForKind(TraceAssert trace, int index, SpanKind spanKind, String operation, Object parentSpan = null, Throwable exception = null,
                                  @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name operation
      kind spanKind
      if (exception) {
        status StatusCode.ERROR
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
