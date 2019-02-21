package datadog.trace.agent.decorator

import io.opentracing.Span
import io.opentracing.tag.Tags

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
      1 * span.setTag(Tags.HTTP_URL.key, "test-url")
      1 * span.setTag(Tags.PEER_HOSTNAME.key, "test-host")
      1 * span.setTag(Tags.PEER_PORT.key, 555)
    }
    0 * _

    where:
    req << [null, [method: "test-method", url: "test-url", host: "test-host", port: 555]]
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
    400    | false | [status: 400]
    499    | false | [status: 499]
    500    | true  | [status: 500]
    600    | true  | [status: 600]
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
  def newDecorator() {
    return new HttpServerDecorator<Map, Map>() {
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
    }
  }
}
