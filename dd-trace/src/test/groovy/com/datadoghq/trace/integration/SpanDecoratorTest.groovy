package com.datadoghq.trace.integration

import com.datadoghq.trace.DDSpanContext
import com.datadoghq.trace.DDTracer
import com.datadoghq.trace.SpanFactory
import io.opentracing.tag.StringTag
import spock.lang.Specification

class SpanDecoratorTest extends Specification {

  def "adding span personalisation using Decorators"() {
    setup:
    def tracer = new DDTracer()
    def decorator = new DDSpanContextDecorator() {

      @Override
      boolean afterSetTag(DDSpanContext context, String tag, Object value) {
        return super.afterSetTag(context, tag, value)
      }

    }
    decorator.setMatchingTag("foo")
    decorator.setMatchingValue("bar")
    decorator.setSetTag("newFoo")
    decorator.setSetValue("newBar")
    tracer.addDecorator(decorator)

    def span = SpanFactory.newSpanTracer(tracer)
    new StringTag("foo").set(span, "bar")

    expect:
    span.getTags().containsKey("newFoo")
    ((String) span.getTags().get("newFoo")) == "newBar"

  }
}
