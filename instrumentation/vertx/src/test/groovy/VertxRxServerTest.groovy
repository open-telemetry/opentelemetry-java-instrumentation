import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.vertx.reactivex.core.Vertx
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.SERVER

class VertxRxServerTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.jdbc.enabled", "true")
  }

  @Shared
  OkHttpClient client = OkHttpUtils.client()

  @Shared
  int port

  @Shared
  Vertx server

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    server = VertxRxWebTestServer.start(port)
  }

  def cleanupSpec() {
    server.close()
  }

  def "test #responseCode response handling"() {
    setup:
    def url = "http://localhost:$port$path"
    def request = new Request.Builder().url(url).get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "HTTP GET" //TODO
          spanKind SERVER
          errored false
          parent()
          tags {
//            "$MoreTags.NET_PEER_PORT" port //TODO
            "$MoreTags.NET_PEER_PORT" Long
            "$MoreTags.NET_PEER_IP" { it == null || it == "127.0.0.1" } // Optional
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
//        basicSpan(it, 1, "io.vertx.core.http.impl.WebSocketRequestHandler.handle", span(0))
//        basicSpan(it, 2, "io.vertx.reactivex.core.http.HttpServer.handle", span(1))
//        basicSpan(it, 3, "io.vertx.ext.web.impl.RouterImpl.handle", span(2))
//        basicSpan(it, 4, "io.vertx.reactivex.ext.web.Route.handle", span(3))
      }
    }

    where:
    responseCode   | path
    SUCCESS.status | SUCCESS.path
  }

  //Verifies that context is correctly propagated and sql query span has correct parent.
  //Tests io.opentelemetry.auto.instrumentation.vertx.reactive.VertxRxInstrumentation
  def "should propagate context over vert.x rx-java framework"() {
    setup:
    def url = "http://localhost:$port/listProducts"
    def request = new Request.Builder().url(url).get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status

    and:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "HTTP GET" //TODO
          spanKind SERVER
          errored false
          parent()
          tags {
            "$MoreTags.NET_PEER_PORT" Long
            "$MoreTags.NET_PEER_IP" { it == null || it == "127.0.0.1" } // Optional
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
//        basicSpan(it, 1, "io.vertx.core.http.impl.WebSocketRequestHandler.handle", span(0))
//        basicSpan(it, 2, "io.vertx.reactivex.core.http.HttpServer.handle", span(1))
//        basicSpan(it, 3, "io.vertx.ext.web.impl.RouterImpl.handle", span(2))
//        basicSpan(it, 4, "io.vertx.reactivex.ext.web.Route.handle", span(3))
        basicSpan(it, 1, "VertxRxWebTestServer.handleListProducts", span(0))
        basicSpan(it, 2, "VertxRxWebTestServer.listProducts", span(1))
        span(3) {
          operationName "SELECT id, name, price, weight FROM products"
          spanKind CLIENT
          childOf span(2)
          errored false
          tags {
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "test?shutdown=true"
            "$Tags.DB_USER" "SA"
            "$Tags.DB_STATEMENT" "SELECT id, name, price, weight FROM products"
            "$Tags.DB_URL" "hsqldb:mem:"
            "span.origin.type" String
          }
        }
      }
    }
  }


}
