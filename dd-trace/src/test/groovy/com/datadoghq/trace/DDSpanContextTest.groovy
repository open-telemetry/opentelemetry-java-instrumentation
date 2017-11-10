package com.datadoghq.trace

import spock.lang.Specification

class DDSpanContextTest extends Specification {

  def "null values for tags are ignored"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, null)
    context.setErrorFlag(true)
    def thread = Thread.currentThread()

    expect:
    context.getTags() == [(DDTags.THREAD_NAME): thread.name, (DDTags.THREAD_ID): thread.id]
    context.serviceName == "fakeService"
    context.resourceName == "fakeResource"
    context.spanType == "fakeType"
    context.toString() == "Span [ t_id=1, s_id=1, p_id=0] trace=fakeService/fakeOperation/fakeResource *errored* tags={thread.id=$thread.id, thread.name=$thread.name}"

    where:
    name << [DDTags.SERVICE_NAME, DDTags.RESOURCE_NAME, DDTags.SPAN_TYPE, "some.tag"]
  }

  def "special tags set certain values"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, value)
    def thread = Thread.currentThread()

    expect:
    context.getTags() == [(DDTags.THREAD_NAME): thread.name, (DDTags.THREAD_ID): thread.id]
    context."$method" == value
    context.toString() == "Span [ t_id=1, s_id=1, p_id=0] trace=$details tags={thread.id=$thread.id, thread.name=$thread.name}"

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
    context.toString() == "Span [ t_id=1, s_id=1, p_id=0] trace=fakeService/fakeOperation/fakeResource tags={$name=$value, thread.id=$thread.id, thread.name=$thread.name}"

    where:
    name             | value
    "some.name"      | "some value"
    "tag with int"   | 1234
    "tag-with-bool"  | false
    "tag_with_float" | 0.321
  }
}
