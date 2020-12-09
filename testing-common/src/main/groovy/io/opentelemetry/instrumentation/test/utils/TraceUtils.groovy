/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter
import io.opentelemetry.instrumentation.api.tracer.Tracer
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.Callable

class TraceUtils {

  private static final BaseInstrumenter tracer = new BaseInstrumenter() {
    @Override
    protected String getInstrumentationName() {
      return "io.opentelemetry.auto"
    }
  }

  static <T> T runUnderServerTrace(final String rootOperationName, final Callable<T> r) {
    try {
      //TODO following two lines are duplicated from io.opentelemetry.instrumentation.api.decorator.HttpServerTracer
      //Find a way to put this management into one place.
      def span = Span.fromContext(tracer.startOperation(rootOperationName, Span.Kind.SERVER))
      Context newContext = Context.current().with(Tracer.CONTEXT_SERVER_SPAN_KEY, span).with(span)

      try {
        def result = newContext.makeCurrent().withCloseable {
          r.call()
        }
        tracer.end(span)
        return result
      } catch (final Exception e) {
        tracer.endExceptionally(span, e)
        throw e
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    try {
      final Span span = Span.fromContext(tracer.startOperation(rootOperationName, Span.Kind.INTERNAL))

      try {
        def result = span.makeCurrent().withCloseable {
          r.call()
        }
        tracer.end(span)
        return result
      } catch (final Exception e) {
        tracer.endExceptionally(span, e)
        throw e
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  static basicSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null) {
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
    }
  }
}
