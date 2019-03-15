package datadog.trace

import datadog.opentracing.SpanFactory
import datadog.trace.api.DDTags
import spock.lang.Specification

class DDSpanContextTest extends Specification {

  def "null values for tags delete existing tags"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag("some.tag", "asdf")
    context.setTag(name, null)
    context.setErrorFlag(true)

    expect:
    context.getTags() == tags
    context.serviceName == "fakeService"
    context.resourceName == "fakeResource"
    context.spanType == "fakeType"
    context.toString() == "DDSpan [ t_id=1, s_id=1, p_id=0] trace=fakeService/fakeOperation/fakeResource metrics={} *errored* tags={${extra}${tags.containsKey(DDTags.SPAN_TYPE) ? "span.type=${context.getSpanType()}, " : ""}thread.id=${Thread.currentThread().id}, thread.name=${Thread.currentThread().name}}"

    where:
    name                 | extra             | tags
    DDTags.SERVICE_NAME  | "some.tag=asdf, " | ["some.tag": "asdf", (DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
    DDTags.RESOURCE_NAME | "some.tag=asdf, " | ["some.tag": "asdf", (DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
    DDTags.SPAN_TYPE     | "some.tag=asdf, " | ["some.tag": "asdf", (DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
    "some.tag"           | ""                | [(DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
  }

  def "special tags set certain values"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, value)
    def thread = Thread.currentThread()

    def expectedTags = [(DDTags.THREAD_NAME): thread.name, (DDTags.THREAD_ID): thread.id]
    def expectedTrace = "DDSpan [ t_id=1, s_id=1, p_id=0] trace=$details metrics={} tags={thread.id=$thread.id, thread.name=$thread.name}"

    expect:
    context.getTags() == expectedTags
    context."$method" == value
    context.toString() == expectedTrace

    where:
    name                 | value                | method         | details
    DDTags.SERVICE_NAME  | "different service"  | "serviceName"  | "different service/fakeOperation/fakeResource"
    DDTags.RESOURCE_NAME | "different resource" | "resourceName" | "fakeService/fakeOperation/different resource"
    DDTags.SPAN_TYPE     | "different type"     | "spanType"     | "fakeService/fakeOperation/fakeResource"
  }

  def "tags can be added to the context"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, value)
    def thread = Thread.currentThread()

    expect:
    context.getTags() == [
      (name)              : value,
      (DDTags.THREAD_NAME): thread.name,
      (DDTags.THREAD_ID)  : thread.id
    ]
    context.toString() == "DDSpan [ t_id=1, s_id=1, p_id=0] trace=fakeService/fakeOperation/fakeResource metrics={} tags={$name=$value, thread.id=$thread.id, thread.name=$thread.name}"

    where:
    name             | value
    "tag.name"       | "some value"
    "tag with int"   | 1234
    "tag-with-bool"  | false
    "tag_with_float" | 0.321
  }

  def "metrics use the expected types"() {
    // floats should be converted to doubles.
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setMetric("test", value)
    def metrics = context.getMetrics()

    expect:
    type.isInstance(metrics["test"])

    where:
    type    | value
    Integer | 0
    Integer | Integer.MAX_VALUE
    Integer | Integer.MIN_VALUE
    Short   | Short.MAX_VALUE
    Short   | Short.MIN_VALUE
    Double  | Float.MAX_VALUE
    Double  | Float.MIN_VALUE
    Double  | Double.MAX_VALUE
    Double  | Double.MIN_VALUE
    Double  | 1f
    Double  | 1d
    Double  | 0.5f
    Double  | 0.5d
    Integer | 0x55
  }
}
