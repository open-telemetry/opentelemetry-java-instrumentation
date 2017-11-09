package com.datadoghq.trace

import spock.lang.Specification

class DDSpanContextTest extends Specification {

  def "null values for tags are ignored"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, null)

    expect:
    context.getTags() == [(DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
    context.serviceName == "fakeService"
    context.resourceName == "fakeResource"
    context.spanType == "fakeType"

    where:
    name << [DDTags.SERVICE_NAME, DDTags.RESOURCE_NAME, DDTags.SPAN_TYPE, "some.tag"]
  }

  def "special tags set certain values"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, value)

    expect:
    context.getTags() == [(DDTags.THREAD_NAME): Thread.currentThread().name, (DDTags.THREAD_ID): Thread.currentThread().id]
    context."$method" == value

    where:
    name                 | value                | method
    DDTags.SERVICE_NAME  | "different service"  | "serviceName"
    DDTags.RESOURCE_NAME | "different resource" | "resourceName"
    DDTags.SPAN_TYPE     | "different type"     | "spanType"
  }

  def "tags can be added to the context"() {
    setup:
    def context = SpanFactory.newSpanOf(0).context
    context.setTag(name, value)

    expect:
    context.getTags() == [
      (name)              : value,
      (DDTags.THREAD_NAME): Thread.currentThread().name,
      (DDTags.THREAD_ID)  : Thread.currentThread().id
    ]

    where:
    name             | value
    "some.name"      | "some value"
    "tag with int"   | 1234
    "tag-with-bool"  | false
    "tag_with_float" | 0.321
  }
}
