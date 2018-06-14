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

import static datadog.opentracing.DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME

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

  def "set service name from servlet.context with context '#context'"() {
    when:
    span.setTag(DDTags.SERVICE_NAME, serviceName)
    span.setTag("servlet.context", context)

    then:
    span.getServiceName() == expected

    where:
    context         | serviceName                     | expected
    "/"             | UNASSIGNED_DEFAULT_SERVICE_NAME | UNASSIGNED_DEFAULT_SERVICE_NAME
    ""              | UNASSIGNED_DEFAULT_SERVICE_NAME | UNASSIGNED_DEFAULT_SERVICE_NAME
    "/some-context" | UNASSIGNED_DEFAULT_SERVICE_NAME | "some-context"
    "other-context" | UNASSIGNED_DEFAULT_SERVICE_NAME | "other-context"
    "/"             | "my-service"                    | "my-service"
    ""              | "my-service"                    | "my-service"
    "/some-context" | "my-service"                    | "my-service"
    "other-context" | "my-service"                    | "my-service"
  }

  def "set operation name"() {
    when:
    Tags.COMPONENT.set(span, component)

    then:
    span.getOperationName() == operationName

    where:
    component << OperationDecorator.MAPPINGS.keySet()
    operationName << OperationDecorator.MAPPINGS.values()
  }

  def "set resource name"() {
    when:
    span.setTag(DDTags.RESOURCE_NAME, name)

    then:
    span.getResourceName() == name

    where:
    name = "my resource name"
  }

  def "set span type"() {
    when:
    span.setTag(DDTags.SPAN_TYPE, type)

    then:
    span.getSpanType() == type

    where:
    type = DDSpanTypes.HTTP_CLIENT
  }

  def "override operation with DBTypeDecorator"() {
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
    when:
    Tags.HTTP_STATUS.set(span, 404)

    then:
    span.getResourceName() == "404"
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
    attribute      | name                 | value
    "serviceName"  | DDTags.SERVICE_NAME  | "my-service"
    "resourceName" | DDTags.RESOURCE_NAME | "my-resource"
    "spanType"     | DDTags.SPAN_TYPE     | "my-span-type"
  }

  def "decorators apply to builder too"() {
    when:
    def span = tracer.buildSpan("decorator.test").withTag("servlet.context", "/my-servlet").start()

    then:
    span.serviceName == "my-servlet"

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.HTTP_STATUS.key, 404).start()

    then:
    span.resourceName == "404"

    when:
    span = tracer.buildSpan("decorator.test").withTag("error", "true").start()

    then:
    span.error

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.HTTP_STATUS.key, 500).start()

    then:
    span.error

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.HTTP_URL.key, "http://example.com/path/number123/?param=true").start()

    then:
    span.resourceName == "/path/?/"

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.DB_STATEMENT.key, "some-statement").start()

    then:
    span.resourceName == "some-statement"
  }
}
