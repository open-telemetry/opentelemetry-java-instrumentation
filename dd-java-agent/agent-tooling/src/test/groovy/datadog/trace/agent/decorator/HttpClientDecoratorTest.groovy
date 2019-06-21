package datadog.trace.agent.decorator

import datadog.trace.api.Config
import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags
import spock.lang.Shared

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

class HttpClientDecoratorTest extends ClientDecoratorTest {

  @Shared
  def testUrl = new URI("http://myhost/somepath")

  def span = Mock(Span)

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      decorator.onRequest(span, req)
    }

    then:
    if (req) {
      1 * span.setTag(Tags.HTTP_METHOD.key, req.method)
      1 * span.setTag(Tags.HTTP_URL.key, "$req.url")
      1 * span.setTag(Tags.PEER_HOSTNAME.key, req.host)
      1 * span.setTag(Tags.PEER_PORT.key, req.port)
      if (renameService) {
        1 * span.setTag(DDTags.SERVICE_NAME, req.host)
      }
    }
    0 * _

    where:
    renameService | req
    false         | null
    true          | null
    false         | [method: "test-method", url: testUrl, host: "test-host", port: 555]
    true          | [method: "test-method", url: testUrl, host: "test-host", port: 555]
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
      1 * span.setTag(Tags.HTTP_URL.key, expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setTag(DDTags.HTTP_QUERY, expectedQuery)
      1 * span.setTag(DDTags.HTTP_FRAGMENT, expectedFragment)
    }
    1 * span.setTag(Tags.HTTP_METHOD.key, null)
    1 * span.setTag(Tags.PEER_HOSTNAME.key, null)
    1 * span.setTag(Tags.PEER_PORT.key, null)
    0 * _

    where:
    tagQueryString | url                                                   | expectedUrl           | expectedQuery      | expectedFragment
    false          | null                                                  | null                  | null               | null
    false          | ""                                                    | "/"                   | ""                 | null
    false          | "/path?query"                                         | "/path"               | ""                 | null
    false          | "https://host:0"                                      | "https://host/"       | ""                 | null
    false          | "https://host/path"                                   | "https://host/path"   | ""                 | null
    false          | "http://host:99/path?query#fragment"                  | "http://host:99/path" | ""                 | null
    true           | null                                                  | null                  | null               | null
    true           | ""                                                    | "/"                   | null               | null
    true           | "/path?encoded+%28query%29%3F"                        | "/path"               | "encoded+(query)?" | null
    true           | "https://host:0"                                      | "https://host/"       | null               | null
    true           | "https://host/path"                                   | "https://host/path"   | null               | null
    true           | "http://host:99/path?query#encoded+%28fragment%29%3F" | "http://host:99/path" | "query"            | "encoded+(fragment)?"

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_CLIENT_ERROR_STATUSES, "$errorRange") {
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
    400    | true  | null       | [status: 400]
    499    | true  | null       | [status: 499]
    500    | false | null       | [status: 500]
    500    | true  | "500"      | [status: 500]
    500    | true  | "400-500"  | [status: 500]
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
      protected String hostname(Map m) {
        return m.host
      }

      @Override
      protected Integer port(Map m) {
        return m.port
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
