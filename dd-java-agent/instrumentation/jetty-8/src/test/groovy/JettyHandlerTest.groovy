import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.DispatcherType
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.atomic.AtomicBoolean

class JettyHandlerTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
  }

  int port = PortUtils.randomOpenPort()
  Server server = new Server(port)

  OkHttpClient client = OkHttpUtils.client()

  def cleanup() {
    server.stop()
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

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "jetty.request"
          resourceName "GET ${handler.class.name}"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "server"
            "component" "jetty-handler"
            "span.origin.type" handler.class.name
            "http.status_code" 200
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            defaultTags()
          }
        }
      }
    }
  }

  def "handler instrumentation clears state after async request"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final Continuation continuation = ContinuationSupport.getContinuation(request)
        continuation.suspend(response)
        // By the way, this is a terrible async server
        new Thread() {
          @Override
          void run() {
            continuation.getServletResponse().setContentType("text/plain;charset=utf-8")
            continuation.getServletResponse().getWriter().println("Hello World")
            continuation.complete()
          }
        }.start()

        baseRequest.setHandled(true)
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def numTraces = 10
    for (int i = 0; i < numTraces; ++i) {
      assert client.newCall(request).execute().body().string().trim() == "Hello World"
    }

    expect:
    assertTraces(numTraces) {
      for (int i = 0; i < numTraces; ++i) {
        trace(i, 1) {
          span(0) {
            serviceName "unnamed-java-app"
            operationName "jetty.request"
            resourceName "GET ${handler.class.name}"
            spanType DDSpanTypes.HTTP_SERVER
          }
        }
      }
    }
  }

  def "call to jetty with error creates a trace"() {
    setup:
    def errorHandlerCalled = new AtomicBoolean(false)
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (baseRequest.dispatcherType == DispatcherType.ERROR) {
          errorHandlerCalled.set(true)
          baseRequest.setHandled(true)
        } else {
          throw new RuntimeException()
        }
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

    assertTraces(errorHandlerCalled.get() ? 2 : 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "jetty.request"
          resourceName "GET ${handler.class.name}"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "server"
            "component" "jetty-handler"
            "span.origin.type" handler.class.name
            "http.status_code" 500
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            errorTags RuntimeException
            defaultTags()
          }
        }
      }
      if (errorHandlerCalled.get()) {
        // FIXME: This doesn't ever seem to be called.
        trace(1, 1) {
          span(0) {
            serviceName "unnamed-java-app"
            operationName "jetty.request"
            resourceName "GET ${handler.class.name}"
            spanType DDSpanTypes.HTTP_SERVER
            errored true
            parent()
            tags {
              "http.url" "http://localhost:$port/"
              "http.method" "GET"
              "span.kind" "server"
              "component" "jetty-handler"
              "span.origin.type" handler.class.name
              "http.status_code" 500
              "peer.hostname" "127.0.0.1"
              "peer.ipv4" "127.0.0.1"
              "peer.port" Integer
              "error" true
              defaultTags()
            }
          }
        }
      }
    }
  }
}
