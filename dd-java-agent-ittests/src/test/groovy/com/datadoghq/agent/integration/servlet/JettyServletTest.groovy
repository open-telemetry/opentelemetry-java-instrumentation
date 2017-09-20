package com.datadoghq.agent.integration.servlet

import com.datadoghq.trace.DDTags
import com.datadoghq.trace.DDTracer
import com.datadoghq.trace.writer.ListWriter
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field

class JettyServletTest extends Specification {

  static final int PORT = randomOpenPort()
  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  private Server jettyServer
  private ServletContextHandler servletContext

  ListWriter writer = new ListWriter()
  DDTracer tracer = new DDTracer(writer)

  def setup() {
    jettyServer = new Server(PORT)
    servletContext = new ServletContextHandler()

    servletContext.addServlet(TestServlet.Sync, "/sync")
    servletContext.addServlet(TestServlet.Async, "/async")

    jettyServer.setHandler(servletContext)
    jettyServer.start()

    System.out.println(
      "Jetty server: http://localhost:" + PORT + "/")

    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    writer.start()
    assert GlobalTracer.isRegistered()
  }

  def cleanup() {
    jettyServer.stop()
    jettyServer.destroy()
  }

  @Unroll
  def "test #path servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$PORT/$path")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == expectedResponse
    writer.size() == 2 // second (parent) trace is the okhttp call above...
    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "Servlet Request - GET"
    !span.context().getErrorFlag()
    span.context().parentId != 0 // parent should be the okhttp call.
    span.context().tags[Tags.HTTP_URL.key] == "http://localhost:$PORT/$path"
    span.context().tags[Tags.HTTP_METHOD.key] == "GET"
    span.context().tags[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_SERVER
    span.context().tags[Tags.COMPONENT.key] == "java-web-servlet"
    span.context().tags[Tags.HTTP_STATUS.key] == 200
    span.context().tags[DDTags.THREAD_NAME] != null
    span.context().tags[DDTags.THREAD_ID] != null
    span.context().tags.size() == 7

    where:
    path    | expectedResponse
    "async" | "Hello Async"
    "sync"  | "Hello Sync"
  }

  private static int randomOpenPort() {
    new ServerSocket(0).withCloseable {
      it.setReuseAddress(true)
      return it.getLocalPort()
    }
  }
}
