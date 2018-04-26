import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JettyHandlerTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
  }

  int port = TestUtils.randomOpenPort()
  Server server = new Server(port)

  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  def cleanup() {
    server.stop()
  }

  @Override
  void afterTest() {
  }

  def "call to jetty creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setContentType("text/plain;charset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)
        baseRequest.setHandled(true)
        response.getWriter().println("Hello World")
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == "Hello World"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def context = trace[0].context()
    context.serviceName == "unnamed-java-app"
    context.operationName == "jetty.request"
    context.resourceName == "GET ${handler.class.name}"
    context.spanType == DDSpanTypes.WEB_SERVLET
    !context.getErrorFlag()
    context.parentId == 0
    def tags = context.tags
    tags["http.url"] == "http://localhost:$port/"
    tags["http.method"] == "GET"
    tags["span.kind"] == "server"
    tags["span.type"] == "web"
    tags["component"] == "jetty-handler"
    tags["http.status_code"] == 200
    tags["thread.name"] != null
    tags["thread.id"] != null
    tags["span.origin.type"] == handler.class.name
    tags.size() == 9
  }

  def "call to jetty with error creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        throw new RuntimeException()
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == ""
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def context = trace[0].context()
    context.serviceName == "unnamed-java-app"
    context.operationName == "jetty.request"
    context.resourceName == "GET ${handler.class.name}"
    context.spanType == DDSpanTypes.WEB_SERVLET
    context.getErrorFlag()
    context.parentId == 0
    def tags = context.tags
    tags["http.url"] == "http://localhost:$port/"
    tags["http.method"] == "GET"
    tags["span.kind"] == "server"
    tags["span.type"] == "web"
    tags["component"] == "jetty-handler"
    tags["http.status_code"] == 500
    tags["thread.name"] != null
    tags["thread.id"] != null
    tags["span.origin.type"] == handler.class.name
    tags["error"] == true
    tags["error.type"] == RuntimeException.name
    tags["error.stack"] != null
    tags.size() == 12
  }
}
