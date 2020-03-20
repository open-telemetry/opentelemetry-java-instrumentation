package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.DDTags.ANALYTICS_SAMPLE_RATE

class SpanDecoratorTest extends DDSpecification {
  static {
    ConfigUtils.updateConfig {
      System.setProperty("dd.$Config.SPLIT_BY_TAGS", "sn.tag1,sn.tag2")
    }
  }

  def cleanupSpec() {
    ConfigUtils.updateConfig {
      System.clearProperty("dd.$Config.SPLIT_BY_TAGS")
    }
  }
  def tracer = DDTracer.builder().writer(new LoggingWriter()).build()
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

  def "set service name"() {
    setup:
    tracer = DDTracer.builder()
      .serviceName("wrong-service")
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .serviceNameMappings(mapping)
      .build()

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
    "sn.tag1"             | "some-service"  | "new-service"
    "sn.tag1"             | "other-service" | "other-service"
    "sn.tag2"             | "some-service"  | "new-service"
    "sn.tag2"             | "other-service" | "other-service"

    mapping = ["some-service": "new-service"]
  }

  def "default or configured service name can be remapped without setting tag"() {
    setup:
    tracer = DDTracer.builder()
      .serviceName(serviceName)
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .serviceNameMappings(mapping)
      .build()

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

  def "mapping causes servlet.context to not change service name"() {
    setup:
    tracer = DDTracer.builder()
      .serviceName(serviceName)
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .serviceNameMappings(mapping)
      .build()

    when:
    def span = tracer.buildSpan("some span").start()
    span.setTag("servlet.context", context)
    span.finish()

    then:
    span.serviceName == "new-service"

    where:
    context         | serviceName
    "/some-context" | DEFAULT_SERVICE_NAME
    "/some-context" | "my-service"

    mapping = [(serviceName): "new-service"]
  }

  static createSplittingTracer(tag) {
    def tracer = DDTracer.builder()
      .serviceName("my-service")
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .build()

    // equivalent to split-by-tags: tag
    tracer.addDecorator(new ServiceNameDecorator(tag, true))

    return tracer
  }

  def "peer.service then split-by-tags via builder"() {
    setup:
    tracer = createSplittingTracer(Tags.MESSAGE_BUS_DESTINATION.key)

    when:
    def span = tracer.buildSpan("some span")
      .withTag(Tags.PEER_SERVICE.key, "peer-service")
      .withTag(Tags.MESSAGE_BUS_DESTINATION.key, "some-queue")
      .start()
    span.finish()

    then:
    span.serviceName == "some-queue"
  }

  def "peer.service then split-by-tags via setTag"() {
    setup:
    tracer = createSplittingTracer(Tags.MESSAGE_BUS_DESTINATION.key)

    when:
    def span = tracer.buildSpan("some span").start()
    span.setTag(Tags.PEER_SERVICE.key, "peer-service")
    span.setTag(Tags.MESSAGE_BUS_DESTINATION.key, "some-queue")
    span.finish()

    then:
    span.serviceName == "some-queue"
  }

  def "split-by-tags then peer-service via builder"() {
    setup:
    tracer = createSplittingTracer(Tags.MESSAGE_BUS_DESTINATION.key)

    when:
    def span = tracer.buildSpan("some span")
      .withTag(Tags.MESSAGE_BUS_DESTINATION.key, "some-queue")
      .withTag(Tags.PEER_SERVICE.key, "peer-service")
      .start()
    span.finish()

    then:
    span.serviceName == "peer-service"
  }

  def "split-by-tags then peer-service via setTag"() {
    setup:
    tracer = createSplittingTracer(Tags.MESSAGE_BUS_DESTINATION.key)

    when:
    def span = tracer.buildSpan("some span").start()
    span.setTag(Tags.MESSAGE_BUS_DESTINATION.key, "some-queue")
    span.setTag(Tags.PEER_SERVICE.key, "peer-service")
    span.finish()

    then:
    span.serviceName == "peer-service"
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
    span.finish()

    then:
    span.getResourceName() == name

    where:
    name = "my resource name"
  }

  def "set span type"() {
    when:
    span.setTag(DDTags.SPAN_TYPE, type)
    span.finish()

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
    span.setTag(ANALYTICS_SAMPLE_RATE, rate)

    then:
    span.metrics.get(ANALYTICS_SAMPLE_RATE) == result

    where:
    rate  | result
    00    | 0
    1     | 1
    0f    | 0
    1f    | 1
    0.1   | 0.1
    1.1   | 1.1
    -1    | -1
    10    | 10
    "00"  | 0
    "1"   | 1
    "1.0" | 1
    "0"   | 0
    "0.1" | 0.1
    "1.1" | 1.1
    "-1"  | -1
    "str" | null
  }

  def "set priority sampling via tag"() {
    when:
    span.setTag(tag, value)

    then:
    span.samplingPriority == expected

    where:
    tag                | value   | expected
    DDTags.MANUAL_KEEP | true    | PrioritySampling.USER_KEEP
    DDTags.MANUAL_KEEP | false   | null
    DDTags.MANUAL_KEEP | "true"  | PrioritySampling.USER_KEEP
    DDTags.MANUAL_KEEP | "false" | null
    DDTags.MANUAL_KEEP | "asdf"  | null

    DDTags.MANUAL_DROP | true    | PrioritySampling.USER_DROP
    DDTags.MANUAL_DROP | false   | null
    DDTags.MANUAL_DROP | "true"  | PrioritySampling.USER_DROP
    DDTags.MANUAL_DROP | "false" | null
    DDTags.MANUAL_DROP | "asdf"  | null
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

  def "set error flag when error tag reported"() {
    when:
    Tags.ERROR.set(span, error)
    span.finish()

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
    span.finish()

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
    def span = tracer.buildSpan("decorator.test").withTag("sn.tag1", "some val").start()
    span.finish()

    then:
    span.serviceName == "some val"

    when:
    span = tracer.buildSpan("decorator.test").withTag("servlet.context", "/my-servlet").start()

    then:
    span.serviceName == "my-servlet"

    when:
    span = tracer.buildSpan("decorator.test").withTag("error", "true").start()
    span.finish()

    then:
    span.error

    when:
    span = tracer.buildSpan("decorator.test").withTag(Tags.DB_STATEMENT.key, "some-statement").start()
    span.finish()

    then:
    span.resourceName == "some-statement"
  }

  def "disable decorator via config"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.trace.${decorator}.enabled", "$enabled")
    }

    tracer = DDTracer.builder()
      .serviceName("some-service")
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .build()

    when:
    def span = tracer.buildSpan("some span").withTag(DDTags.SERVICE_NAME, "other-service").start()
    span.finish()

    then:
    span.getServiceName() == enabled ? "other-service" : "some-service"

    cleanup:
    ConfigUtils.updateConfig {
      System.clearProperty("dd.trace.${decorator}.enabled")
    }

    where:
    decorator                                          | enabled
    ServiceNameDecorator.getSimpleName().toLowerCase() | true
    ServiceNameDecorator.getSimpleName()               | true
    ServiceNameDecorator.getSimpleName().toLowerCase() | false
    ServiceNameDecorator.getSimpleName()               | false
  }

  def "disabling service decorator does not disable split by tags"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.trace." + ServiceNameDecorator.getSimpleName().toLowerCase() + ".enabled", "false")
    }

    tracer = DDTracer.builder()
      .serviceName("some-service")
      .writer(new LoggingWriter())
      .sampler(new AllSampler())
      .build()

    when:
    def span = tracer.buildSpan("some span").withTag(tag, name).start()
    span.finish()

    then:
    span.getServiceName() == expected

    cleanup:
    ConfigUtils.updateConfig {
      System.clearProperty("dd.trace." + ServiceNameDecorator.getSimpleName().toLowerCase() + ".enabled")
    }

    where:
    tag                 | name          | expected
    DDTags.SERVICE_NAME | "new-service" | "some-service"
    "service"           | "new-service" | "some-service"
    "sn.tag1"           | "new-service" | "new-service"


  }
}
