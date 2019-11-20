package datadog.opentracing

import datadog.opentracing.propagation.ExtractedContext
import datadog.trace.api.Config
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.Scope
import io.opentracing.noop.NoopSpan

import static java.util.concurrent.TimeUnit.MILLISECONDS

class DDSpanBuilderTest extends DDSpecification {

  def writer = new ListWriter()
  def config = Config.get()
  def tracer = new DDTracer(writer)

  def "build simple span"() {
    setup:
    final DDSpan span = tracer.buildSpan("op name").start()

    expect:
    span.operationName == "op name"
  }

  def "build complex span"() {
    setup:
    def expectedName = "fakeName"
    def tags = [
      "1": true,
      "2": "fakeString",
      "3": 42.0,
    ]

    DDTracer.DDSpanBuilder builder = tracer
      .buildSpan(expectedName)
    tags.each {
      builder = builder.withTag(it.key, it.value)
    }

    when:
    DDSpan span = builder.start()

    then:
    span.getOperationName() == expectedName
    span.tags.subMap(tags.keySet()) == tags

    when:
    // with all custom fields provided

    span =
      tracer
        .buildSpan(expectedName)
        .withErrorFlag()
        .start()

    final DDSpanContext context = span.context()

    then:
    context.getErrorFlag()
  }

  def "setting #name should remove"() {
    setup:
    final DDSpan span = tracer.buildSpan("op name")
      .withTag(name, "tag value")
      .withTag(name, value)
      .start()

    expect:
    span.tags[name] == null

    when:
    span.setTag(name, "a tag")

    then:
    span.tags[name] == "a tag"

    when:
    span.setTag(name, (String) value)

    then:
    span.tags[name] == null

    where:
    name        | value
    "null.tag"  | null
    "empty.tag" | ""
  }

  def "should build span timestamp in nano"() {
    setup:
    // time in micro
    final long expectedTimestamp = 487517802L * 1000 * 1000L
    final String expectedName = "fakeName"

    DDSpan span =
      tracer
        .buildSpan(expectedName)
        .withStartTimestamp(expectedTimestamp)
        .start()

    expect:
    // get return nano time
    span.getStartTime() == expectedTimestamp * 1000L

    when:
    // auto-timestamp in nanoseconds
    def start = System.currentTimeMillis()
    span = tracer.buildSpan(expectedName).start()
    def stop = System.currentTimeMillis()

    then:
    // Give a range of +/- 5 millis
    span.getStartTime() >= MILLISECONDS.toNanos(start - 1)
    span.getStartTime() <= MILLISECONDS.toNanos(stop + 1)
  }

  def "should link to parent span"() {
    setup:
    final BigInteger spanId = 1G
    final BigInteger expectedParentId = spanId

    final DDSpanContext mockedContext = Mock()
    1 * mockedContext.getTraceId() >> spanId
    1 * mockedContext.getSpanId() >> spanId
    1 * mockedContext.getTrace() >> new PendingTrace(tracer, 1G)

    final String expectedName = "fakeName"

    final DDSpan span =
      tracer
        .buildSpan(expectedName)
        .asChildOf(mockedContext)
        .start()

    final DDSpanContext actualContext = span.context()

    expect:
    actualContext.getParentId() == expectedParentId
    actualContext.getTraceId() == spanId
  }

  def "should link to parent span implicitly"() {
    setup:
    final Scope parent = noopParent ?
      tracer.scopeManager().activate(NoopSpan.INSTANCE, false) :
      tracer.buildSpan("parent")
        .startActive(false)

    final BigInteger expectedParentId = noopParent ? 0G : new BigInteger(parent.span().context().toSpanId())

    final String expectedName = "fakeName"

    final DDSpan span = tracer
      .buildSpan(expectedName)
      .start()

    final DDSpanContext actualContext = span.context()

    expect:
    actualContext.getParentId() == expectedParentId

    cleanup:
    parent.close()

    where:
    noopParent << [false, true]
  }

  def "should inherit the DD parent attributes"() {
    setup:
    def expectedName = "fakeName"

    final DDSpan parent =
      tracer
        .buildSpan(expectedName)
        .start()

    DDSpan span =
      tracer
        .buildSpan(expectedName)
        .asChildOf(parent)
        .start()

    expect:
    span.getOperationName() == expectedName
  }

  def "should track all spans in trace"() {
    setup:
    List<DDSpan> spans = []
    final int nbSamples = 10

    // root (aka spans[0]) is the parent
    // others are just for fun

    def root = tracer.buildSpan("fake_O").start()
    spans.add(root)

    final long tickEnd = System.currentTimeMillis()

    for (int i = 1; i <= 10; i++) {
      def span = tracer
        .buildSpan("fake_" + i)
        .asChildOf(spans.get(i - 1))
        .start()
      spans.add(span)
      span.finish()
    }
    root.finish(tickEnd)

    expect:
    root.context().getTrace().size() == nbSamples + 1
    root.context().getTrace().containsAll(spans)
    spans[(int) (Math.random() * nbSamples)].context.trace.containsAll(spans)
  }

  def "ExtractedContext should populate new span details"() {
    setup:
    final DDSpan span = tracer.buildSpan("op name")
      .asChildOf(extractedContext).start()

    expect:
    span.traceId == extractedContext.traceId
    span.parentId == extractedContext.spanId

    where:
    extractedContext             | _
    new ExtractedContext(1G, 2G) | _
    new ExtractedContext(3G, 4G) | _
  }
}
