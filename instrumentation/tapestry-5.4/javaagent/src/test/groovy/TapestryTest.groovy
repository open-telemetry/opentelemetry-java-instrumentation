/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.jsoup.Jsoup

class TapestryTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  static OkHttpClient client = OkHttpUtils.client(true)

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    // set up test application
    webAppContext.setBaseResource(Resource.newResource("src/test/webapp"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  static serverSpan(TraceAssert trace, int index, String spanName) {
    trace.span(index) {
      hasNoParent()

      name spanName
      kind SpanKind.SERVER
    }
  }

  def "test index page"() {
    setup:
    def url = HttpUrl.get(address.resolve("")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()
    def doc = Jsoup.parse(response.body().string())

    expect:
    response.code() == 200
    doc.selectFirst("title").text() == "Index page"

    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/Index")
        basicSpan(it, 1, "activate/Index", span(0))
      }
    }
  }

  def "test start action"() {
    setup:
    // index.start triggers an action named "start" on index page
    def url = HttpUrl.get(address.resolve("index.start")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()
    def doc = Jsoup.parse(response.body().string())

    expect:
    response.code() == 200
    doc.selectFirst("title").text() == "Other page"

    assertTraces(2) {
      trace(0, 4) {
        serverSpan(it, 0, getContextPath() + "/Index")
        basicSpan(it, 1, "activate/Index", span(0))
        basicSpan(it, 2, "action/Index:start", span(0))
        basicSpan(it, 3, "HttpServletResponseWrapper.sendRedirect", span(2))
      }
      trace(1, 2) {
        serverSpan(it, 0, getContextPath() + "/Other")
        basicSpan(it, 1, "activate/Other", span(0))
      }
    }
  }

  def "test exception action"() {
    setup:
    // index.exception triggers an action named "exception" on index page
    def url = HttpUrl.get(address.resolve("index.exception")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()

    expect:
    response.code() == 500

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          hasNoParent()
          kind SpanKind.SERVER
          name getContextPath() + "/Index"
          status ERROR
        }
        basicSpan(it, 1, "activate/Index", span(0))
        basicSpan(it, 2, "action/Index:exception", span(0), new IllegalStateException("expected"))
      }
    }
  }

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
      .header("User-Agent", TEST_USER_AGENT)
      .header("X-Forwarded-For", TEST_CLIENT_IP)
  }
}
