package io.opentelemetry.auto.test.utils

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.decorator.BaseDecorator
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.trace.SpanData
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Tracer
import lombok.SneakyThrows

import java.util.concurrent.Callable

class TraceUtils {

  private static final BaseDecorator DECORATOR = new BaseDecorator() {
    protected String[] instrumentationNames() {
      return new String[0]
    }

    protected String spanType() {
      return null
    }

    protected String component() {
      return null
    }
  }

  private static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto")

  @SneakyThrows
  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final Span span = TRACER.spanBuilder(rootOperationName).startSpan()
    DECORATOR.afterStart(span)

    Scope scope = TRACER.withSpan(span)

    try {
      return r.call()
    } catch (final Exception e) {
      DECORATOR.onError(span, e)
      throw e
    } finally {
      DECORATOR.beforeFinish(span)
      span.end()
      scope.close()
    }
  }

  static basicSpan(TraceAssert trace, int index, String operation, String resource = null, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      operationName operation
      errored exception != null
      tags {
        "$MoreTags.RESOURCE_NAME" resource
        if (exception) {
          errorTags(exception.class, exception.message)
        }
      }
    }
  }
}
