package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags

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

  def "set span type"() {
    when:
    span.setTag(DDTags.SPAN_TYPE, type)

    then:
    span.getSpanType() == type

    where:
    type = DDSpanTypes.HTTP_CLIENT
  }


  def "set span type with DBTypeDecorator"() {
    when:
    Tags.DB_TYPE.set(span, type)

    then:
    span.context().getSpanType() == "sql"


    when:
    Tags.DB_TYPE.set(span, "mongo")

    then:
    span.context().getSpanType() == "mongodb"

    where:
    type = "foo"
  }

  def "set 5XX status code as an error"() {
    when:
    Tags.HTTP_STATUS.set(span, status)

    then:
    span.isError() == error

    where:
    status | error
    400    | false
    404    | false
    499    | false
    500    | true
    550    | true
    599    | true
    600    | false
  }

  def "set error flag when error tag reported"() {
    when:
    Tags.ERROR.set(span, error)

    then:
    span.isError() == error

    where:
    error | _
    true  | _
    false | _
  }

  def "#attribute decorators apply to builder too"() {
    setup:
    def span = tracer.buildSpan("decorator.test").withTag(name, value).start()

    expect:
    span.context()."$attribute" == value

    where:
    attribute  | name             | value
    "spanType" | DDTags.SPAN_TYPE | "my-span-type"
  }

  def "decorators apply to builder too"() {
    when:
    span = tracer.buildSpan("decorator.test").withTag("error", "true").start()

    then:
    span.error

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.HTTP_STATUS.key, 500).start()

    then:
    span.error
  }
}
