package datadog.trace.common.processor


import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.common.processor.rule.URLAsResourceNameRule
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.tag.Tags
import spock.lang.Subject

class URLAsResourceNameRuleTest extends DDSpecification {

  def writer = new ListWriter()
  def tracer = DDTracer.builder().writer(writer).build()

  @Subject
  def decorator = new URLAsResourceNameRule()

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
    setup:
    def span = SpanFactory.newSpanOf(0)
    span.context.resourceName = null
    meta.each {
      span.setTag(it.key, (String) it.value)
    }

    when:
    decorator.processSpan(span, meta, [span])

    then:
    span.resourceName == resourceName

    where:
    value                       | resourceName        | meta
    null                        | "fakeOperation"     | [:]
    " "                         | "/"                 | [:]
    "\t"                        | "/"                 | [:]
    "/path"                     | "/path"             | [:]
    "/ABC/a-1/b_2/c.3/d4d/5f/6" | "/ABC/?/?/?/?/?/?"  | [:]
    "/not-found"                | "fakeOperation"     | [(Tags.HTTP_STATUS.key): "404"]
    "/with-method"              | "POST /with-method" | [(Tags.HTTP_METHOD.key): "Post"]

    ignore = meta.put(Tags.HTTP_URL.key, value)
  }
}
