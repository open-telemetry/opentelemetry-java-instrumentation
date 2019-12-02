package datadog.opentracing

import datadog.opentracing.propagation.ExtractedContext
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.SpanContext

import java.util.concurrent.TimeUnit

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME

class DDSpanTest extends DDSpecification {

  def writer = new ListWriter()
  def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer, [:])

  def "getters and setters"() {
    setup:
    final DDSpanContext context =
      new DDSpanContext(
        1G,
        1G,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G),
        tracer)

    final DDSpan span = new DDSpan(1L, context)

    when:
    span.setServiceName("service")
    then:
    span.getServiceName() == "service"

    when:
    span.setOperationName("operation")
    then:
    span.getOperationName() == "operation"

    when:
    span.setResourceName("resource")
    then:
    span.getResourceName() == "resource"

    when:
    span.setSpanType("type")
    then:
    span.getSpanType() == "type"
  }

  def "resource name equals operation name if null"() {
    setup:
    final String opName = "operationName"
    DDSpan span

    when:
    span = tracer.buildSpan(opName).start()
    then:
    span.getResourceName() == opName
    span.getServiceName() == DEFAULT_SERVICE_NAME

    when:
    final String resourceName = "fake"
    final String serviceName = "myService"
    span = new DDTracer()
      .buildSpan(opName)
      .withResourceName(resourceName)
      .withServiceName(serviceName)
      .start()
    then:
    span.getResourceName() == resourceName
    span.getServiceName() == serviceName
  }

  def "duration measured in nanoseconds"() {
    setup:
    def mod = TimeUnit.MILLISECONDS.toNanos(1)
    def builder = tracer.buildSpan("test")
    def start = System.nanoTime()
    def span = builder.start()
    def between = System.nanoTime()
    def betweenDur = System.nanoTime() - between
    span.finish()
    def total = System.nanoTime() - start

    expect:
    // Generous 5 seconds to execute this test
    Math.abs(TimeUnit.NANOSECONDS.toSeconds(span.startTime) - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) < 5
    span.durationNano > betweenDur
    span.durationNano < total
    span.durationNano % mod > 0 // Very slim chance of a false negative.
  }

  def "starting with a timestamp disables nanotime"() {
    setup:
    def mod = TimeUnit.MILLISECONDS.toNanos(1)
    def start = System.currentTimeMillis()
    def builder = tracer.buildSpan("test")
      .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()))
    def span = builder.start()
    def between = System.currentTimeMillis()
    def betweenDur = System.currentTimeMillis() - between
    span.finish()
    def total = Math.max(1, System.currentTimeMillis() - start)

    expect:
    // Generous 5 seconds to execute this test
    Math.abs(TimeUnit.NANOSECONDS.toSeconds(span.startTime) - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) < 5
    span.durationNano >= TimeUnit.MILLISECONDS.toNanos(betweenDur)
    span.durationNano <= TimeUnit.MILLISECONDS.toNanos(total)
    span.durationNano % mod == 0 || span.durationNano == 1
  }

  def "stopping with a timestamp disables nanotime"() {
    setup:
    def mod = TimeUnit.MILLISECONDS.toNanos(1)
    def builder = tracer.buildSpan("test")
    def start = System.currentTimeMillis()
    def span = builder.start()
    def between = System.currentTimeMillis()
    def betweenDur = System.currentTimeMillis() - between
    span.finish(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis() + 1))
    def total = System.currentTimeMillis() - start + 1

    expect:
    // Generous 5 seconds to execute this test
    Math.abs(TimeUnit.NANOSECONDS.toSeconds(span.startTime) - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) < 5
    span.durationNano >= TimeUnit.MILLISECONDS.toNanos(betweenDur)
    span.durationNano <= TimeUnit.MILLISECONDS.toNanos(total)
    span.durationNano % mod == 0
  }

  def "stopping with a timestamp after start time yeilds a min duration of 1"() {
    setup:
    def span = tracer.buildSpan("test").start()
    span.finish(span.startTimeMicro - 10)

    expect:
    span.durationNano == 1
  }

  def "isRootSpan() in and not in the context of distributed tracing"() {
    setup:
    def root = tracer.buildSpan("root").asChildOf((SpanContext) extractedContext).start()
    def child = tracer.buildSpan("child").asChildOf(root).start()

    expect:
    root.isRootSpan() == isTraceRootSpan
    !child.isRootSpan()

    cleanup:
    child.finish()
    root.finish()

    where:
    extractedContext                 | isTraceRootSpan
    null                             | true
    new ExtractedContext(123G, 456G) | false
  }

  def "getApplicationRootSpan() in and not in the context of distributed tracing"() {
    setup:
    def root = tracer.buildSpan("root").asChildOf((SpanContext) extractedContext).start()
    def child = tracer.buildSpan("child").asChildOf(root).start()

    expect:
    root.localRootSpan == root
    child.localRootSpan == root
    // Checking for backward compatibility method names
    root.rootSpan == root
    child.rootSpan == root

    cleanup:
    child.finish()
    root.finish()

    where:
    extractedContext                 | isTraceRootSpan
    null                             | true
    new ExtractedContext(123G, 456G) | false
  }
}
