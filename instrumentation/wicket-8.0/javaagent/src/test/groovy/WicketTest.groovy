/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import hello.HelloApplication
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import javax.servlet.DispatcherType
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.wicket.protocol.http.WicketFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource
import org.jsoup.Jsoup

class WicketTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    ServletContextHandler context = new ServletContextHandler(0)
    context.setContextPath(getContextPath())
    def resource = new FileResource(getClass().getResource("/"))
    context.setBaseResource(resource)
    server.setHandler(context)

    context.addServlet(DefaultServlet, "/")
    def registration = context.getServletContext().addFilter("WicketApplication", WicketFilter)
    registration.setInitParameter("applicationClassName", HelloApplication.getName())
    registration.setInitParameter("filterMappingUrlPattern", "/wicket-test/*")
    registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/wicket-test/*")

    server.start()

    return server
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

  def "test hello"() {
    setup:
    def url = HttpUrl.get(address.resolve("wicket-test/")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()
    def doc = Jsoup.parse(response.body().string())

    expect:
    response.code() == 200
    doc.selectFirst("#message").text() == "Hello World!"

    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, getContextPath() + "/wicket-test/hello.HelloPage")
      }
    }
  }

  def "test exception"() {
    setup:
    def url = HttpUrl.get(address.resolve("wicket-test/exception")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()

    expect:
    response.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, getContextPath() + "/wicket-test/org.apache.wicket.markup.html.pages.InternalErrorPage", null, new Exception("test exception"))
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
