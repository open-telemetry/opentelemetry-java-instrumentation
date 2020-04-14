package datadog.trace.bootstrap.instrumentation.decorator


import datadog.trace.api.Config
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import datadog.trace.bootstrap.instrumentation.api.Tags

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

class HttpServerDecoratorTest extends ServerDecoratorTest {

  def span = Mock(AgentSpan)

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest(span, req)

    then:
    if (req) {
      1 * span.setTag(Tags.HTTP_METHOD, "test-method")
      1 * span.setTag(Tags.HTTP_URL, url)
    }
    0 * _

    where:
    req                                                                    | url
    null                                                                   | _
    [method: "test-method", url: URI.create("http://test-url?some=query")] | "http://test-url/"
    [method: "test-method", url: URI.create("http://a:80/")]               | "http://a/"
    [method: "test-method", url: URI.create("https://10.0.0.1:443")]       | "https://10.0.0.1/"
    [method: "test-method", url: URI.create("https://localhost:0/1/")]     | "https://localhost/1/"
    [method: "test-method", url: URI.create("http://123:8080/some/path")]  | "http://123:8080/some/path"
  }

  def "test url handling for #url"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_SERVER_TAG_QUERY_STRING, "$tagQueryString") {
      decorator.onRequest(span, req)
    }

    then:
    if (expectedUrl) {
      1 * span.setTag(Tags.HTTP_URL, expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setTag(DDTags.HTTP_QUERY, expectedQuery)
      1 * span.setTag(DDTags.HTTP_FRAGMENT, expectedFragment)
    }
    1 * span.setTag(Tags.HTTP_METHOD, null)
    0 * _

    where:
    tagQueryString | url                                                    | expectedUrl           | expectedQuery       | expectedFragment
    false          | null                                                   | null                  | null                | null
    false          | ""                                                     | "/"                   | ""                  | null
    false          | "/path?query"                                          | "/path"               | ""                  | null
    false          | "https://host:0"                                       | "https://host/"       | ""                  | null
    false          | "https://host/path"                                    | "https://host/path"   | ""                  | null
    false          | "http://host:99/path?query#fragment"                   | "http://host:99/path" | ""                  | null
    true           | null                                                   | null                  | null                | null
    true           | ""                                                     | "/"                   | null                | null
    true           | "/path?encoded+%28query%29%3F?"                        | "/path"               | "encoded+(query)??" | null
    true           | "https://host:0"                                       | "https://host/"       | null                | null
    true           | "https://host/path"                                    | "https://host/path"   | null                | null
    true           | "http://host:99/path?query#enc+%28fragment%29%3F"      | "http://host:99/path" | "query"             | "enc+(fragment)?"
    true           | "http://host:99/path?query#enc+%28fragment%29%3F?tail" | "http://host:99/path" | "query"             | "enc+(fragment)??tail"

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onConnection"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onConnection(span, conn)

    then:
    if (conn) {
      1 * span.setTag(Tags.PEER_PORT, 555)
      if (ipv4) {
        1 * span.setTag(Tags.PEER_HOST_IPV4, "10.0.0.1")
      } else if (ipv4 != null) {
        1 * span.setTag(Tags.PEER_HOST_IPV6, "3ffe:1900:4545:3:200:f8ff:fe21:67cf")
      }
    }
    0 * _

    where:
    ipv4  | conn
    null  | null
    null  | [ip: null, port: 555]
    true  | [ip: "10.0.0.1", port: 555]
    false | [ip: "3ffe:1900:4545:3:200:f8ff:fe21:67cf", port: 555]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onResponse(span, resp)

    then:
    if (status) {
      1 * span.setTag(Tags.HTTP_STATUS, status)
    }
    0 * _

    where:
    status | resp
    200    | [status: 200]
    399    | [status: 399]
    400    | [status: 400]
    404    | [status: 404]
    404    | [status: 404]
    499    | [status: 499]
    500    | [status: 500]
    600    | [status: 600]
    null   | [status: null]
    null   | null
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest(null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onResponse(null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator() {
    return new HttpServerDecorator<Map, Map, Map>() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String component() {
        return "test-component"
      }

      @Override
      protected String method(Map m) {
        return m.method
      }

      @Override
      protected URI url(Map m) {
        return m.url
      }

      @Override
      protected String peerHostIP(Map m) {
        return m.ip
      }

      @Override
      protected Integer peerPort(Map m) {
        return m.port
      }

      @Override
      protected Integer status(Map m) {
        return m.status
      }
    }
  }
}
