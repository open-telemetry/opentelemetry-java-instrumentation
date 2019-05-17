package datadog.opentracing

import datadog.opentracing.propagation.ExtractedContext
import datadog.opentracing.propagation.TagContext
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.writer.ListWriter
import io.opentracing.SpanContext
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME

class DDSpanTest extends Specification {
  def writer = new ListWriter()
  def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer, new RateByServiceSampler(), [:])

  @Shared
  def defaultSamplingPriority = PrioritySampling.SAMPLER_KEEP

  def "getters and setters"() {
    setup:
    final DDSpanContext context =
      new DDSpanContext(
        "1",
        "1",
        "0",
        "fakeService",
        "fakeOperation",
        "fakeResource",
        PrioritySampling.UNSET,
        null,
        Collections.<String, String> emptyMap(),
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, "1", [:]),
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
    span.getType() == "type"

    when:
    span.setSamplingPriority(PrioritySampling.UNSET)
    then:
    span.getSamplingPriority() == null

    when:
    span.setSamplingPriority(PrioritySampling.SAMPLER_KEEP)
    then:
    span.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP

    when:
    context.lockSamplingPriority()
    span.setSamplingPriority(PrioritySampling.USER_KEEP)
    then:
    span.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
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

  def "priority sampling metric set only on root span"() {
    setup:
    def parent = tracer.buildSpan("testParent").start()
    def child1 = tracer.buildSpan("testChild1").asChildOf(parent).start()

    child1.setSamplingPriority(PrioritySampling.SAMPLER_KEEP)
    child1.context().lockSamplingPriority()
    parent.setSamplingPriority(PrioritySampling.SAMPLER_DROP)
    child1.finish()
    def child2 = tracer.buildSpan("testChild2").asChildOf(parent).start()
    child2.finish()
    parent.finish()

    expect:
    parent.context().samplingPriority == PrioritySampling.SAMPLER_KEEP
    parent.getMetrics().get(DDSpanContext.PRIORITY_SAMPLING_KEY) == PrioritySampling.SAMPLER_KEEP
    child1.getSamplingPriority() == parent.getSamplingPriority()
    child1.getMetrics().get(DDSpanContext.PRIORITY_SAMPLING_KEY) == null
    child2.getSamplingPriority() == parent.getSamplingPriority()
    child2.getMetrics().get(DDSpanContext.PRIORITY_SAMPLING_KEY) == null
  }

  def "origin set only on root span"() {
    setup:
    def parent = tracer.buildSpan("testParent").asChildOf(extractedContext).start().context()
    def child = tracer.buildSpan("testChild1").asChildOf(parent).start().context()

    expect:
    parent.origin == "some-origin"
    parent.@origin == "some-origin" // Access field directly instead of getter.
    child.origin == "some-origin"
    child.@origin == null // Access field directly instead of getter.

    where:
    extractedContext                                           | _
    new TagContext("some-origin", [:])                         | _
    new ExtractedContext("1", "2", 0, "some-origin", [:], [:]) | _
  }

  def "isRootSpan() in and not in the context of distributed tracing"() {
    setup:
    def root = tracer.buildSpan("root").asChildOf((SpanContext)extractedContext).start()
    def child = tracer.buildSpan("child").asChildOf(root).start()

    expect:
    root.isRootSpan() == isTraceRootSpan
    !child.isRootSpan()

    cleanup:
    child.finish()
    root.finish()

    where:
    extractedContext | isTraceRootSpan
    null | true
    new ExtractedContext("123", "456", 1, "789", [:], [:]) | false
  }

  def "getApplicationRootSpan() in and not in the context of distributed tracing"() {
    setup:
    def root = tracer.buildSpan("root").asChildOf((SpanContext)extractedContext).start()
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
    extractedContext | isTraceRootSpan
    null | true
    new ExtractedContext("123", "456", 1, "789", [:], [:]) | false
  }

  def "setting forced tracing via tag"() {

    setup:
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }

    expect:
    span.getSamplingPriority() == expectedPriority

    cleanup:
    span.finish()

    where:
    tagName | tagValue | expectedPriority
    'manual.drop' | true | PrioritySampling.USER_DROP
    'manual.keep' | true | PrioritySampling.USER_KEEP
  }

  def "not setting forced tracing via tag or setting it wrong value not causing exception"() {

    setup:
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }

    expect:
    span.getSamplingPriority() == defaultSamplingPriority

    cleanup:
    span.finish()

    where:
    tagName | tagValue
    // When no tag is set default to
    null | null
    // Setting to not known value
    'manual.drop' | false
    'manual.keep' | false
    'manual.drop' | 1
    'manual.keep' | 1
  }
}
