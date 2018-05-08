package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.common.writer.LoggingWriter
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import spock.lang.Specification

class SpanDecoratorTest extends Specification {
  def tracer = new DDTracer(new LoggingWriter())
  def span = SpanFactory.newSpanOf(tracer)

  def "adding span personalisation using Decorators"() {
    setup:
    def decorator = new AbstractDecorator() {

      @Override
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

  def "set service name"() {
    setup:
    tracer.addDecorator(new ServiceNameDecorator(mapping))

    when:
    span.setTag(DDTags.SERVICE_NAME, name)

    then:
    span.getServiceName() == expected

    where:
    name            | expected        | mapping
    "some-service"  | "new-service"   | ["some-service": "new-service"]
    "other-service" | "other-service" | ["some-service": "new-service"]
  }

  def "set operation name"() {
    setup:
    tracer.addDecorator(new OperationDecorator())

    when:
    Tags.COMPONENT.set(span, component)

    then:
    span.getOperationName() == operationName

    where:
    component << OperationDecorator.MAPPINGS.keySet()
    operationName << OperationDecorator.MAPPINGS.values()
  }

  def "set resource name"() {
    setup:
    tracer.addDecorator(new ResourceNameDecorator())

    when:
    span.setTag(DDTags.RESOURCE_NAME, name)

    then:
    span.getResourceName() == name

    where:
    name = "my resource name"
  }

  def "set span type"() {
    setup:
    tracer.addDecorator(new SpanTypeDecorator())

    when:
    span.setTag(DDTags.SPAN_TYPE, type)

    then:
    span.getSpanType() == type

    where:
    type = DDSpanTypes.HTTP_CLIENT
  }

  def "override operation with DBTypeDecorator"() {
    setup:
    tracer.addDecorator(new DBTypeDecorator())

    when:
    Tags.DB_TYPE.set(span, type)

    then:
    span.getOperationName() == type + ".query"
    span.context().getSpanType() == "sql"


    when:
    Tags.DB_TYPE.set(span, "mongo")

    then:
    span.getOperationName() == "mongo.query"
    span.context().getSpanType() == "mongodb"

    where:
    type = "foo"
  }

  def "DBStatementAsResource should not interact on Mongo queries"() {
    setup:
    tracer.addDecorator(new DBStatementAsResourceName())

    when:
    span.setResourceName("not-change-me")
    Tags.COMPONENT.set(span, "java-mongo")
    Tags.DB_STATEMENT.set(span, something)

    then:
    span.getResourceName() == "not-change-me"


    when:
    span.setResourceName("change-me")
    Tags.COMPONENT.set(span, "other-contrib")
    Tags.DB_STATEMENT.set(span, something)

    then:
    span.getResourceName() == something

    where:
    something = "fake-query"
  }

  def "set 404 as a resource on a 404 issue"() {
    setup:
    tracer.addDecorator(new Status404Decorator())

    when:
    Tags.HTTP_STATUS.set(span, 404)

    then:
    span.getResourceName() == "404"
  }

  def "set 5XX status code as an error"() {
    setup:
    tracer.addDecorator(new Status5XXDecorator())

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
    setup:
    tracer.addDecorator(new ErrorFlag())

    when:
    Tags.ERROR.set(span, error)

    then:
    span.isError() == error

    where:
    error | _
    true  | _
    false | _
  }
}
