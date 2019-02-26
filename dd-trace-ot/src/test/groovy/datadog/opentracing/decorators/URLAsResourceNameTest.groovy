package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.tag.Tags
import spock.lang.Specification
import spock.lang.Subject

class URLAsResourceNameTest extends Specification {
  def writer = new ListWriter()
  def tracer = new DDTracer(writer)

  @Subject
  def decorator = new URLAsResourceName()

  def "remove query params"() {
    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input                          | output
    ""                             | "/"
    " "                            | "/"
    "\t"                           | "/"
    "/"                            | "/"
    "/?asdf"                       | "/"
    "/search"                      | "/search"
    "/search?"                     | "/search"
    "/search?id=100&private=true"  | "/search"
    "/search?id=100&private=true?" | "/search"
  }

  def "should replace all digits"() {
    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input              | output
    "/1"               | "/?"
    "/9999"            | "/?"
    "/user/1"          | "/user/?"
    "/user/1/"         | "/user/?/"
    "/user/1/repo/50"  | "/user/?/repo/?"
    "/user/1/repo/50/" | "/user/?/repo/?/"
  }

  def "should replace segments with mixed-characters"() {
    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input                                              | output
    "/a1/v2"                                           | "/?/?"
    "/v3/1a"                                           | "/v3/?"
    "/V01/v9/abc/-1?"                                  | "/V01/v9/abc/?"
    "/ABC/av-1/b_2/c.3/d4d/v5f/v699/7"                 | "/ABC/?/?/?/?/?/?/?"
    "/user/asdf123/repository/01234567-9ABC-DEF0-1234" | "/user/?/repository/?"
  }

  def "should leave other segments alone"() {
    when:
    def norm = decorator.norm(input)

    then:
    norm == input

    where:
    input      | _
    "/a-b"     | _
    "/a_b"     | _
    "/a.b"     | _
    "/a-b/a-b" | _
    "/a_b/a_b" | _
    "/a.b/a.b" | _
  }

  def "sets the resource name"() {
    when:
    final DDSpanContext context =
      new DDSpanContext(
        "1",
        "1",
        "0",
        "fakeService",
        "fakeOperation",
        "fakeResource",
        PrioritySampling.UNSET,
        null,
        Collections.<String, String> emptyMap(),
        false,
        "fakeType",
        tags,
        new PendingTrace(tracer, "1", [:]),
        tracer)

    then:
    decorator.shouldSetTag(context, Tags.HTTP_URL.getKey(), value)
    context.resourceName == resourceName

    where:
    value                       | resourceName        | tags
    "/path"                     | "/path"             | [:]
    "/ABC/a-1/b_2/c.3/d4d/5f/6" | "/ABC/?/?/?/?/?/?"  | [:]
    "/not-found"                | "fakeResource"      | [(Tags.HTTP_STATUS.key): 404]
    "/with-method"              | "Post /with-method" | [(Tags.HTTP_METHOD.key): "Post"]
  }
}
