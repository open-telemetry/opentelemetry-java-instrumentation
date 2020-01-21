package io.opentelemetry.auto.test.utils

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.decorator.BaseDecorator
import io.opentelemetry.auto.instrumentation.api.AgentScope
import io.opentelemetry.auto.instrumentation.api.AgentSpan
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import lombok.SneakyThrows

import java.util.concurrent.Callable

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan

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

  @SneakyThrows
  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final AgentSpan span = startSpan(rootOperationName)
    DECORATOR.afterStart(span)

    AgentScope scope = activateSpan(span, true)

    try {
      return r.call()
    } catch (final Exception e) {
      DECORATOR.onError(span, e)
      throw e
    } finally {
      DECORATOR.beforeFinish(span)
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
