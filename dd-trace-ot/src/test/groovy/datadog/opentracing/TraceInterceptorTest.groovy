package datadog.opentracing

import datadog.trace.api.GlobalTracer
import datadog.trace.api.interceptor.MutableSpan
import datadog.trace.api.interceptor.TraceInterceptor
import datadog.trace.common.writer.ListWriter
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class TraceInterceptorTest extends Specification {
  def writer = new ListWriter()
  def tracer = new DDTracer(writer)

  def "interceptor is registered as a service"() {
    expect:
    tracer.interceptors.iterator().hasNext()
    tracer.interceptors.iterator().next() instanceof TestInterceptor
  }

  def "interceptors with the same priority replaced"() {
    setup:
    int priority = 999
    ((TestInterceptor) tracer.interceptors.iterator().next()).priority = priority
    tracer.interceptors.add(new TraceInterceptor() {
      @Override
      Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace) {
        return null
      }

      @Override
      int priority() {
        return priority
      }
    })

    expect:
    tracer.interceptors.size() == 1
    tracer.interceptors.iterator().next() instanceof TestInterceptor
  }

  def "interceptors with different priority sorted"() {
    setup:
    def priority = score
    def existingInterceptor = tracer.interceptors.iterator().next()
    def newInterceptor = new TraceInterceptor() {
      @Override
      Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace) {
        return null
      }

      @Override
      int priority() {
        return priority
      }
    }
    tracer.interceptors.add(newInterceptor)

    expect:
    tracer.interceptors == reverse ? [newInterceptor, existingInterceptor] : [existingInterceptor, newInterceptor]

    where:
    score | reverse
    -1    | false
    1     | true
  }

  def "interceptor can discard a trace (p=#score)"() {
    setup:
    def called = new AtomicBoolean(false)
    def priority = score
    tracer.interceptors.add(new TraceInterceptor() {
      @Override
      Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace) {
        called.set(true)
        return Collections.emptyList()
      }

      @Override
      int priority() {
        return priority
      }
    })
    tracer.buildSpan("test").start().finish()

    expect:
    tracer.interceptors.size() == Math.abs(score) + 1
    (called.get()) == (score != 0)
    (writer == []) == (score != 0)

    where:
    score | _
    -1    | _
    0     | _
    1     | _
  }

  def "interceptor can modify a span"() {
    setup:
    tracer.interceptors.add(new TraceInterceptor() {
      @Override
      Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace) {
        for (MutableSpan span : trace) {
          span
            .setOperationName("modifiedON-" + span.getOperationName())
            .setServiceName("modifiedSN-" + span.getServiceName())
            .setResourceName("modifiedRN-" + span.getResourceName())
            .setSpanType("modifiedST-" + span.getSpanType())
            .setTag("boolean-tag", true)
            .setTag("number-tag", 5.0)
            .setTag("string-tag", "howdy")
            .setError(true)
        }
        return trace
      }

      @Override
      int priority() {
        return 1
      }
    })
    tracer.buildSpan("test").start().finish()

    expect:
    def trace = writer.firstTrace()
    trace.size() == 1

    def span = trace[0]

    span.context().operationName == "modifiedON-test"
    span.serviceName == "modifiedSN-unnamed-java-app"
    span.resourceName == "modifiedRN-modifiedON-test"
    span.type == "modifiedST-null"
    span.context().getErrorFlag()

    def tags = span.context().tags

    tags["boolean-tag"] == true
    tags["number-tag"] == 5.0
    tags["string-tag"] == "howdy"

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags["runtime-id"] != null
    tags["language"] != null
    tags.size() == 7
  }

  def "register interceptor through bridge"() {
    setup:
    GlobalTracer.registerIfAbsent(tracer)
    def interceptor = new TraceInterceptor() {
      @Override
      Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace) {
        return trace
      }

      @Override
      int priority() {
        return 38
      }
    }

    expect:
    GlobalTracer.get().addTraceInterceptor(interceptor)
    tracer.interceptors.contains(interceptor)
  }
}
