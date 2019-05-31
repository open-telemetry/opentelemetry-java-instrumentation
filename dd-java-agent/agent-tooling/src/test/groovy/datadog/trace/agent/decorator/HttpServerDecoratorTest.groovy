package datadog.trace.agent.decorator

import datadog.trace.api.Config
import io.opentracing.Span
import io.opentracing.tag.Tags

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

class HttpServerDecoratorTest extends ServerDecoratorTest {

  def span = Mock(Span)

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest(span, req)

    then:
    if (req) {
      1 * span.setTag(Tags.HTTP_METHOD.key, "test-method")
      1 * span.setTag(Tags.HTTP_URL.key, url)
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
      1 * span.setTag(Tags.HTTP_URL.key, expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setTag("http.query.string", expectedQuery)
      1 * span.setTag("http.fragment.string", expectedFragment)
    }
    1 * span.setTag(Tags.HTTP_METHOD.key, null)
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
      1 * span.setTag(Tags.PEER_HOSTNAME.key, "test-host")
      1 * span.setTag(Tags.PEER_PORT.key, 555)
      if (ipv4) {
        1 * span.setTag(Tags.PEER_HOST_IPV4.key, "10.0.0.1")
      } else if (ipv4 != null) {
        1 * span.setTag(Tags.PEER_HOST_IPV6.key, "3ffe:1900:4545:3:200:f8ff:fe21:67cf")
      }
    }
    0 * _

    where:
    ipv4  | conn
    null  | null
    null  | [host: "test-host", ip: null, port: 555]
    true  | [host: "test-host", ip: "10.0.0.1", port: 555]
    false | [host: "test-host", ip: "3ffe:1900:4545:3:200:f8ff:fe21:67cf", port: 555]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_SERVER_ERROR_STATUSES, "$errorRange") {
      decorator.onResponse(span, resp)
    }

    then:
    if (status) {
      1 * span.setTag(Tags.HTTP_STATUS.key, status)
    }
    if (error) {
      1 * span.setTag(Tags.ERROR.key, true)
    }
    0 * _

    where:
    status | error | errorRange | resp
    200    | false | null       | [status: 200]
    399    | false | null       | [status: 399]
    400    | false | null       | [status: 400]
    404    | true  | "404"      | [status: 404]
    404    | true  | "400-500"  | [status: 404]
    499    | false | null       | [status: 499]
    500    | true  | null       | [status: 500]
    600    | false | null       | [status: 600]
    null   | false | null       | [status: null]
    null   | false | null       | null
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
      protected String peerHostname(Map m) {
        return m.host
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
