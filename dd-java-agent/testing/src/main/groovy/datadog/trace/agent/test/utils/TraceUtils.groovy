package datadog.trace.agent.test.utils

import datadog.trace.agent.decorator.BaseDecorator
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
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

  @SneakyThrows
  static <T extends Object> Object runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final Scope scope = GlobalTracer.get().buildSpan(rootOperationName).startActive(true)
    DECORATOR.afterStart(scope)
    ((TraceScope) scope).setAsyncPropagation(true)

    try {
      return r.call()
    } catch (final Exception e) {
      DECORATOR.onError(scope, e)
      throw e
    } finally {
      DECORATOR.beforeFinish(scope)
      scope.close()
    }
  }

  static basicSpan(TraceAssert trace, int index, String spanName, Throwable exception = null) {
    trace.span(index) {
      parent()
      serviceName "unnamed-java-app"
      operationName spanName
      resourceName spanName
      errored exception != null
      tags {
        defaultTags()
        if (exception) {
          errorTags(exception.class, exception.message)
        }
      }
    }
  }
}
