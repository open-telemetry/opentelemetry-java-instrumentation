package datadog.trace.common.processor

import datadog.opentracing.SpanFactory
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.common.processor.rule.Status404Rule
import datadog.trace.common.processor.rule.URLAsResourceNameRule
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.Tags
import spock.lang.Subject

class TraceProcessorTest extends DDSpecification {

  @Subject
  def processor = new TraceProcessor()

  def span = SpanFactory.newSpanOf(0)
  def trace = [span]

  def setup() {
    span.context.resourceName = null
  }

  def "test disable"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.trace.${name}.enabled", "false")
    }
    def processor = new TraceProcessor()

    expect:
    !processor.rules.any {
      it.class.name == rule.name
    }

    cleanup:
    ConfigUtils.updateConfig {
      System.clearProperty("dd.trace.${name}.enabled")
    }

    where:
    rule                  | alias
    URLAsResourceNameRule | null
    URLAsResourceNameRule | URLAsResourceNameRule.simpleName.toLowerCase()
    URLAsResourceNameRule | "URLAsResourceName"
    Status404Rule         | null
    Status404Rule         | Status404Rule.simpleName.toLowerCase()
    Status404Rule         | "Status404Decorator"

    name = alias == null ? rule.simpleName : alias
  }

  def "set 404 as a resource on a 404 issue"() {
    setup:
    Tags.HTTP_STATUS.set(span, 404)

    when:
    processor.onTraceComplete(trace)

    then:
    span.getResourceName() == "404"
  }

  def "status #status code error=#error"() {
    setup:
    Tags.HTTP_STATUS.set(span, status)

    when:
    processor.onTraceComplete(trace)

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

  def "resource name set with url path #url to #resourceName"() {
    setup:
    if (method) {
      Tags.HTTP_METHOD.set(span, method)
    }
    Tags.HTTP_URL.set(span, url)
    Tags.HTTP_STATUS.set(span, status)

    when:
    processor.onTraceComplete(trace)

    then:
    span.resourceName == resourceName

    where:
    method | url      | status | resourceName
    "GET"  | ""       | 200    | "fakeOperation"
    null   | "/"      | 200    | "/"
    null   | "/path"  | 200    | "/path"
    "put"  | "/"      | 200    | "PUT /"
    "Head" | "/path"  | 200    | "HEAD /path"
    "post" | "/post"  | 400    | "POST /post"
    "GET"  | "/asdf"  | 404    | "404"
    null   | "/error" | 500    | "/error"
  }
}
