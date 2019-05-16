package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.writer.LoggingWriter
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import spock.lang.Specification

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.DDTags.EVENT_SAMPLE_RATE
import static java.util.Collections.emptyMap

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
    tracer = new DDTracer(
      "wrong-service",
      new LoggingWriter(),
      new AllSampler(),
      "some-runtime-id",
      emptyMap(),
      emptyMap(),
      mapping,
      emptyMap()
    )

    when:
    def span = tracer.buildSpan("some span").withTag(tag, name).start()
    span.finish()

    then:
    span.getServiceName() == expected

    where:
    tag                   | name            | expected
    DDTags.SERVICE_NAME   | "some-service"  | "new-service"
    DDTags.SERVICE_NAME   | "other-service" | "other-service"
    "service"             | "some-service"  | "new-service"
    "service"             | "other-service" | "other-service"
    Tags.PEER_SERVICE.key | "some-service"  | "new-service"
    Tags.PEER_SERVICE.key | "other-service" | "other-service"

    mapping = ["some-service": "new-service"]
  }

  def "default or configured service name can be remapped without setting tag"() {
    setup:
    tracer = new DDTracer(
      serviceName,
      new LoggingWriter(),
      new AllSampler(),
      "some-runtime-id",
      emptyMap(),
      emptyMap(),
      mapping,
      emptyMap()
    )

    when:
    def span = tracer.buildSpan("some span").start()
    span.finish()

    then:
    span.serviceName == expected

    where:
    serviceName          | expected             | mapping
    DEFAULT_SERVICE_NAME | DEFAULT_SERVICE_NAME | ["other-service-name": "other-service"]
    DEFAULT_SERVICE_NAME | "new-service"        | [(DEFAULT_SERVICE_NAME): "new-service"]
    "other-service-name" | "other-service"      | ["other-service-name": "other-service"]
  }

  def "set service name from servlet.context with context '#context'"() {
    when:
    span.setTag(DDTags.SERVICE_NAME, serviceName)
    span.setTag("servlet.context", context)

    then:
    span.serviceName == expected

    where:
    context         | serviceName          | expected
    "/"             | DEFAULT_SERVICE_NAME | DEFAULT_SERVICE_NAME
    ""              | DEFAULT_SERVICE_NAME | DEFAULT_SERVICE_NAME
    "/some-context" | DEFAULT_SERVICE_NAME | "some-context"
    "other-context" | DEFAULT_SERVICE_NAME | "other-context"
    "/"             | "my-service"         | "my-service"
    ""              | "my-service"         | "my-service"
    "/some-context" | "my-service"         | "my-service"
    "other-context" | "my-service"         | "my-service"
  }

  def "set service name from servlet.context with context '#context' for service #serviceName"() {
    setup:
    tracer = new DDTracer(
      serviceName,
      new LoggingWriter(),
      new AllSampler(),
      "some-runtime-id",
      emptyMap(),
      emptyMap(),
      mapping,
      emptyMap()
    )

    when:
    def span = tracer.buildSpan("some span").start()
    span.setTag("servlet.context", context)
    span.finish()

    then:
    span.serviceName == expected

    where:
    context         | serviceName          | expected
    "/"             | DEFAULT_SERVICE_NAME | "new-service"
    ""              | DEFAULT_SERVICE_NAME | "new-service"
    "/some-context" | DEFAULT_SERVICE_NAME | "some-context"
    "other-context" | DEFAULT_SERVICE_NAME | "other-context"
    "/"             | "my-service"         | "new-service"
    ""              | "my-service"         | "new-service"
    "/some-context" | "my-service"         | "new-service"
    "other-context" | "my-service"         | "new-service"

    mapping = [(serviceName): "new-service"]
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

  def "span metrics starts empty but added with rate limiting value of #rate"() {
    expect:
    span.metrics == [:]

    when:
    span.setTag(EVENT_SAMPLE_RATE, rate)

    then:
    span.metrics == result

    where:
    rate  | result
    00    | [(EVENT_SAMPLE_RATE): 0]
    1     | [(EVENT_SAMPLE_RATE): 1]
    0f    | [(EVENT_SAMPLE_RATE): 0]
    1f    | [(EVENT_SAMPLE_RATE): 1]
    0.1   | [(EVENT_SAMPLE_RATE): 0.1]
    1.1   | [(EVENT_SAMPLE_RATE): 1.1]
    -1    | [(EVENT_SAMPLE_RATE): -1]
    10    | [(EVENT_SAMPLE_RATE): 10]
    "00"  | [:]
    "str" | [:]
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
