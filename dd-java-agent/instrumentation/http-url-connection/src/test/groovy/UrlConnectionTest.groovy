import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.http_url_connection.UrlInstrumentation
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionInstrumentation.HttpUrlState.OPERATION_NAME

class UrlConnectionTest extends AgentTestRunner {

  def "trace request with connection failure #scheme"() {
    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        URLConnection connection = url.openConnection()
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(10000)
        assert GlobalTracer.get().scopeManager().active() != null
        connection.inputStream
      }
    }

    then:
    thrown ConnectException

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          tags {
            errorTags ConnectException, String
            defaultTags()
          }
        }
        span(1) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET /"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url/"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" UNUSABLE_PORT
            errorTags ConnectException, String
            defaultTags()
          }
        }
      }
    }

    where:
    scheme  | renameService
    "http"  | true
    "https" | false

    url = new URI("$scheme://localhost:$UNUSABLE_PORT").toURL()
  }

  def "trace request with connection failure to a local file with broken url path"() {
    setup:
    def url = new URI("file:/some-random-file%abc").toURL()

    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        url.openConnection()
      }
    }

    then:
    thrown IllegalArgumentException

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          tags {
            errorTags IllegalArgumentException, String
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "file.request"
          resourceName "$url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT.key" UrlInstrumentation.COMPONENT
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            // FIXME: These tags really make no sense for non-http connections, why do we set them?
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.PEER_PORT.key" 80
            errorTags IllegalArgumentException, String
            defaultTags()
          }
        }
      }
    }

    where:
    renameService << [false, true]
  }
}
