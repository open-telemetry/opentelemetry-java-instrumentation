package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.StringTag

class SpanDecoratorTest extends DDSpecification {

  def tracer = new DDTracer(new LoggingWriter())
  def span = SpanFactory.newSpanOf(tracer)

  def "adding span personalisation using Decorators"() {
    setup:
    def decorator = new AbstractDecorator() {
      boolean shouldSetTag(DDSpanContext context, String tag, Object value) {
        return super.shouldSetTag(context, tag, value)
      }
    }
    decorator.setMatchingTag("foo")
    decorator.setMatchingValue("bar")
    decorator.setReplacementTag("newFoo")
    decorator.setReplacementValue("newBar")
    tracer.addDecorator(decorator)

    new StringTag("foo").set(span, "bar")

    expect:
    span.getTags().containsKey("newFoo")
    span.getTags().get("newFoo") == "newBar"
  }
}
