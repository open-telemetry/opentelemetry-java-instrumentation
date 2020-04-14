import com.twitter.finatra.http.HttpServer
import com.twitter.util.Await
import com.twitter.util.Closable
import com.twitter.util.Duration
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.finatra.FinatraDecorator

import java.util.concurrent.TimeoutException

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class FinatraServerTest extends HttpServerTest<HttpServer> {
  private static final Duration TIMEOUT = Duration.fromSeconds(5)
  private static final long STARTUP_TIMEOUT = 20 * 1000

  static closeAndWait(Closable closable) {
    if (closable != null) {
      Await.ready(closable.close(), TIMEOUT)
    }
  }

  @Override
  HttpServer startServer(int port) {
    HttpServer testServer = new FinatraServer()

    // Starting the server is blocking so start it in a separate thread
    Thread startupThread = new Thread({
      testServer.main("-admin.port=:0", "-http.port=:" + port)
    })
    startupThread.setDaemon(true)
    startupThread.start()

    long startupDeadline = System.currentTimeMillis() + STARTUP_TIMEOUT
    while (!testServer.started()) {
      if (System.currentTimeMillis() > startupDeadline) {
        throw new TimeoutException("Timed out waiting for server startup")
      }
    }

    return testServer
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  @Override
  boolean testNotFound() {
    // Resource name is set to "GET /notFound"
    false
  }

  @Override
  void stopServer(HttpServer httpServer) {
    Await.ready(httpServer.close(), TIMEOUT)
  }

  @Override
  String component() {
    return FinatraDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    return "finatra.request"
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    def errorEndpoint = endpoint == EXCEPTION || endpoint == ERROR
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "finatra.controller"
      resourceName "FinatraController"
      spanType DDSpanTypes.HTTP_SERVER
      errored errorEndpoint
      childOf(parent as DDSpan)
      tags {
        "$Tags.COMPONENT" FinatraDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER

        // Finatra doesn't propagate the stack trace or exception to the instrumentation
        // so the normal errorTags() method can't be used
        defaultTags()
      }
    }
  }
}
