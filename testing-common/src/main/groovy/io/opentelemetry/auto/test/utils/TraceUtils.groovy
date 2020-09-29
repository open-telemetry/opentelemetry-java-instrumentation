/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.test.utils

import static io.opentelemetry.context.ContextUtils.withScopedContext
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith
import static io.opentelemetry.trace.TracingContextUtils.withSpan

import io.grpc.Context
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.Span
import java.util.concurrent.Callable

class TraceUtils {

  private static final BaseTracer TRACER = new BaseTracer() {
    @Override
    protected String getInstrumentationName() {
      return "io.opentelemetry.auto"
    }
  }

  static <T> T runUnderServerTrace(final String rootOperationName, final Callable<T> r) {
    try {
      //TODO following two lines are duplicated from io.opentelemetry.instrumentation.api.decorator.HttpServerTracer
      //Find a way to put this management into one place.
      def span = TRACER.startSpan(rootOperationName, Span.Kind.SERVER)
      Context newContext = withSpan(span, Context.current().withValue(BaseTracer.CONTEXT_SERVER_SPAN_KEY, span))


      try {
        def result = withScopedContext(newContext).withCloseable {
          r.call()
        }
        TRACER.end(span)
        return result
      } catch (final Exception e) {
        TRACER.endExceptionally(span, e)
        throw e
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    try {
      final Span span = TRACER.startSpan(rootOperationName, Span.Kind.INTERNAL)

      try {
        def result = currentContextWith(span).withCloseable {
          r.call()
        }
        TRACER.end(span)
        return result
      } catch (final Exception e) {
        TRACER.endExceptionally(span, e)
        throw e
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  static basicSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      operationName operation
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }
    }
  }
}
