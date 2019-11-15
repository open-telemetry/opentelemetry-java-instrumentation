package datadog.opentracing.decorators

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.Tags
import spock.lang.Subject

class URLAsResourceNameTest extends DDSpecification {

  def writer = new ListWriter()
  def tracer = new DDTracer(writer)

  @Subject
  def decorator = new URLAsResourceName()

  def "pulls path from url #input"() {
    when:
    def path = decorator.rawPathFromUrlString(input)

    then:
    path == expected

    where:
    input                                                            | expected
    ""                                                               | "/"
    "/"                                                              | "/"
    "/?asdf"                                                         | "/"
    "/search"                                                        | "/search"
    "/search?"                                                       | "/search"
    "/search?id=100&private=true"                                    | "/search"
    "/search?id=100&private=true?"                                   | "/search"
    "http://localhost"                                               | "/"
    "http://localhost/"                                              | "/"
    "http://localhost/?asdf"                                         | "/"
    "http://local.host:8080/search"                                  | "/search"
    "https://localhost:443/search?"                                  | "/search"
    "http://local.host:80/search?id=100&private=true"                | "/search"
    "http://localhost:80/search?id=100&private=true?"                | "/search"
    "http://10.0.0.1/?asdf"                                          | "/"
    "http://127.0.0.1/?asdf"                                         | "/"
    "http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]:80/index.html" | "/index.html"
    "http://[1080:0:0:0:8:800:200C:417A]/index.html"                 | "/index.html"
    "http://[3ffe:2a00:100:7031::1]"                                 | "/"
    "http://[1080::8:800:200C:417A]/foo"                             | "/foo"
    "http://[::192.9.5.5]/ipng"                                      | "/ipng"
    "http://[::FFFF:129.144.52.38]:80/index.html"                    | "/index.html"
    "http://[2010:836B:4179::836B:4179]"                             | "/"
  }

  def "should replace all digits"() {
    when:
    def norm = decorator.normalizePath(input)

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
    def norm = decorator.normalizePath(input)

    then:
    norm == output

    where:
    input                                              | output
    "/a1/v2"                                           | "/?/?"
    "/v3/1a"                                           | "/v3/?"
    "/V01/v9/abc/-1"                                   | "/V01/v9/abc/?"
    "/ABC/av-1/b_2/c.3/d4d/v5f/v699/7"                 | "/ABC/?/?/?/?/?/?/?"
    "/user/asdf123/repository/01234567-9ABC-DEF0-1234" | "/user/?/repository/?"
  }

  def "should leave other segments alone"() {
    when:
    def norm = decorator.normalizePath(input)

    then:
    norm == input

    where:
    input      | _
    "/v0/"     | _
    "/v10/xyz" | _
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
        1G,
        1G,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        PrioritySampling.UNSET,
        null,
        Collections.<String, String> emptyMap(),
        false,
        "fakeType",
        tags,
        new PendingTrace(tracer, 1G, [:]),
        tracer)

    then:
    decorator.shouldSetTag(context, Tags.HTTP_URL.getKey(), value)
    context.resourceName == resourceName

    where:
    value                       | resourceName        | tags
    null                        | "fakeResource"      | [:]
    " "                         | "/"                 | [:]
    "\t"                        | "/"                 | [:]
    "/path"                     | "/path"             | [:]
    "/ABC/a-1/b_2/c.3/d4d/5f/6" | "/ABC/?/?/?/?/?/?"  | [:]
    "/not-found"                | "fakeResource"      | [(Tags.HTTP_STATUS.key): 404]
    "/with-method"              | "Post /with-method" | [(Tags.HTTP_METHOD.key): "Post"]
  }
}
