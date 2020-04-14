package datadog.trace.bootstrap.instrumentation.decorator

import datadog.trace.api.Config
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import datadog.trace.bootstrap.instrumentation.api.Tags
import spock.lang.Shared

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

class HttpClientDecoratorTest extends ClientDecoratorTest {

  @Shared
  def testUrl = new URI("http://myhost:123/somepath")

  def span = Mock(AgentSpan)

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      decorator.onRequest(span, req)
    }

    then:
    if (req) {
      1 * span.setTag(Tags.HTTP_METHOD, req.method)
      1 * span.setTag(Tags.HTTP_URL, "$req.url")
      1 * span.setTag(Tags.PEER_HOSTNAME, req.url.host)
      1 * span.setTag(Tags.PEER_PORT, req.url.port)
      if (renameService) {
        1 * span.setTag(DDTags.SERVICE_NAME, req.url.host)
      }
    }
    0 * _

    where:
    renameService | req
    false         | null
    true          | null
    false         | [method: "test-method", url: testUrl]
    true          | [method: "test-method", url: testUrl]
  }

  def "test url handling for #url"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_TAG_QUERY_STRING, "$tagQueryString") {
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
    if (hostname) {
      1 * span.setTag(Tags.PEER_HOSTNAME, hostname)
    }
    if (port) {
      1 * span.setTag(Tags.PEER_PORT, port)
    }
    0 * _

    where:
    tagQueryString | url                                                   | expectedUrl           | expectedQuery      | expectedFragment      | hostname | port
    false          | null                                                  | null                  | null               | null                  | null     | null
    false          | ""                                                    | "/"                   | ""                 | null                  | null     | null
    false          | "/path?query"                                         | "/path"               | ""                 | null                  | null     | null
    false          | "https://host:0"                                      | "https://host/"       | ""                 | null                  | "host"   | null
    false          | "https://host/path"                                   | "https://host/path"   | ""                 | null                  | "host"   | null
    false          | "http://host:99/path?query#fragment"                  | "http://host:99/path" | ""                 | null                  | "host"   | 99
    true           | null                                                  | null                  | null               | null                  | null     | null
    true           | ""                                                    | "/"                   | null               | null                  | null     | null
    true           | "/path?encoded+%28query%29%3F"                        | "/path"               | "encoded+(query)?" | null                  | null     | null
    true           | "https://host:0"                                      | "https://host/"       | null               | null                  | "host"   | null
    true           | "https://host/path"                                   | "https://host/path"   | null               | null                  | "host"   | null
    true           | "http://host:99/path?query#encoded+%28fragment%29%3F" | "http://host:99/path" | "query"            | "encoded+(fragment)?" | "host"   | 99

    req = [url: url == null ? null : new URI(url)]
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
    499    | [status: 499]
    500    | [status: 500]
    500    | [status: 500]
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
  def newDecorator(String serviceName = "test-service") {
    return new HttpClientDecorator<Map, Map>() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String service() {
        return serviceName
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
      protected Integer status(Map m) {
        return m.status
      }

      protected boolean traceAnalyticsDefault() {
        return true
      }
    }
  }
}
