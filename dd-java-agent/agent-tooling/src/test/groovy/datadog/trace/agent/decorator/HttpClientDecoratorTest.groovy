package datadog.trace.agent.decorator

import datadog.trace.api.Config
import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags

import static datadog.trace.agent.test.utils.TraceUtils.withConfigOverride

class HttpClientDecoratorTest extends ClientDecoratorTest {

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
      1 * span.setTag(Tags.HTTP_METHOD.key, "test-method")
      1 * span.setTag(Tags.HTTP_URL.key, "test-url")
      1 * span.setTag(Tags.PEER_HOSTNAME.key, "test-host")
      1 * span.setTag(Tags.PEER_PORT.key, 555)
      if (renameService) {
        1 * span.setTag(DDTags.SERVICE_NAME, "test-host")
      }
    }
    0 * _

    where:
    renameService | req
    false         | null
    true          | null
    false         | [method: "test-method", url: "test-url", host: "test-host", port: 555]
    true          | [method: "test-method", url: "test-url", host: "test-host", port: 555]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onResponse(span, resp)

    then:
    if (status) {
      1 * span.setTag(Tags.HTTP_STATUS.key, status)
    }
    if (error) {
      1 * span.setTag(Tags.ERROR.key, true)
    }
    0 * _

    where:
    status | error | resp
    200    | false | [status: 200]
    399    | false | [status: 399]
    400    | true  | [status: 400]
    499    | true  | [status: 499]
    500    | false | [status: 500]
    600    | false | [status: 600]
    null   | false | [status: null]
    null   | false | null
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
      protected String url(Map m) {
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
