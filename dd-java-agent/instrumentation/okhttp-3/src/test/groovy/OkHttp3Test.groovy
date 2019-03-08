import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.withConfigOverride
import static java.util.concurrent.TimeUnit.SECONDS

class OkHttp3Test extends AgentTestRunner {
  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      all {
        response.status(200).send("pong")
      }
    }
  }

  def client = new OkHttpClient()

  def "sending a request creates spans and sends headers"() {
    setup:
    def requestBulder = new Request.Builder()
      .url("http://localhost:$server.address.port/ping")
    if (agentRequest) {
      requestBulder.addHeader("Datadog-Meta-Lang", "java")
    }
    def request = requestBulder.build()

    Response response = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      if (!async) {
        return client.newCall(request).execute()
      }

      AtomicReference<Response> responseRef = new AtomicReference()
      def latch = new CountDownLatch(1)

      client.newCall(request).enqueue(new Callback() {
        void onResponse(Call call, Response response) {
          responseRef.set(response)
          latch.countDown()
        }

        void onFailure(Call call, IOException e) {
          latch.countDown()
        }
      })
      latch.await(10, SECONDS)
      return responseRef.get()
    }

    expect:
    response.body.string() == "pong"
    if (agentRequest) {
      assert TEST_WRITER.size() == 0
      assert server.lastRequest.headers.get("x-datadog-trace-id") == null
      assert server.lastRequest.headers.get("x-datadog-parent-id") == null
    } else {
      assertTraces(1) {
        trace(0, 2) {
          span(0) {
            operationName "okhttp.http"
            serviceName "okhttp"
            resourceName "okhttp.http"
            spanType DDSpanTypes.HTTP_CLIENT
            errored false
            parent()
            tags {
              "$Tags.COMPONENT.key" "okhttp"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(1) {
            operationName "okhttp.http"
            serviceName renameService ? "localhost" : "okhttp"
            resourceName "GET /ping"
            spanType DDSpanTypes.HTTP_CLIENT
            errored false
            childOf(span(0))
            tags {
              defaultTags()
              "$Tags.COMPONENT.key" "okhttp-network"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              "$Tags.HTTP_METHOD.key" "GET"
              "$Tags.HTTP_STATUS.key" 200
              "$Tags.HTTP_URL.key" "http://localhost:$server.address.port/ping"
              "$Tags.PEER_HOSTNAME.key" "localhost"
              "$Tags.PEER_PORT.key" server.address.port
              "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            }
          }
        }
      }

      assert server.lastRequest.headers.get("x-datadog-trace-id") == TEST_WRITER[0][1].traceId
      assert server.lastRequest.headers.get("x-datadog-parent-id") == TEST_WRITER[0][1].spanId
    }

    where:
    renameService | async | agentRequest
    false         | false | false
    true          | false | false
    false         | true  | false
    true          | true  | false
    false         | false | true
    false         | false | true
  }

  def "sending an invalid request creates an error span"() {
    setup:
    def unusablePort = PortUtils.UNUSABLE_PORT
    def request = new Request.Builder()
      .url("http://localhost:$unusablePort/ping")
      .build()

    when:
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      client.newCall(request).execute()
    }

    then:
    thrown(ConnectException)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "okhttp.http"
          serviceName "okhttp"
          resourceName "okhttp.http"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "okhttp"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            errorTags(ConnectException, ~/Failed to connect to localhost\/[\d:\.]+:61/)
            defaultTags()
          }
        }
      }
    }

    where:
    renameService << [false, true]
  }
}
