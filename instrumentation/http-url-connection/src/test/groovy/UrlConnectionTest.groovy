import io.opentelemetry.auto.api.Config
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.bootstrap.AgentClassLoader
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.http_url_connection.UrlInstrumentation
import io.opentelemetry.auto.test.AgentTestRunner

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan
import static io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionInstrumentation.HttpUrlState.OPERATION_NAME
import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class UrlConnectionTest extends AgentTestRunner {

  def "trace request with connection failure #scheme"() {
    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        URLConnection connection = url.openConnection()
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(10000)
        assert activeSpan() != null
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
          }
        }
        span(1) {
          operationName OPERATION_NAME
          childOf span(0)
          errored true
          tags {
            "$MoreTags.SERVICE_NAME" renameService ? "localhost" : null
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "http-url-connection"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_PORT" UNUSABLE_PORT
            "$Tags.HTTP_URL" "$url/"
            "$Tags.HTTP_METHOD" "GET"
            errorTags ConnectException, String
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
          }
        }
        span(1) {
          operationName "file.request"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" UrlInstrumentation.COMPONENT
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_PORT" 80
            // FIXME: These tags really make no sense for non-http connections, why do we set them?
            "$Tags.HTTP_URL" "$url"
            errorTags IllegalArgumentException, String
          }
        }
      }
    }

    where:
    renameService << [false, true]
  }

  def "AgentClassloader ClassNotFoundException doesn't create span"() {
    given:
    ClassLoader agentLoader = new AgentClassLoader(null, null, null)
    ClassLoader childLoader = new URLClassLoader(new URL[0], agentLoader)

    when:
    runUnderTrace("someTrace") {
      childLoader.loadClass("io.opentelemetry.auto.doesnotexist")
    }

    then:
    thrown ClassNotFoundException

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          tags {
            errorTags ClassNotFoundException, String
          }
        }
      }
    }
  }
}
