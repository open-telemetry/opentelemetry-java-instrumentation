/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import com.twitter.finatra.http.HttpServer
import com.twitter.util.Await
import com.twitter.util.Closable
import com.twitter.util.Duration
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeoutException

class FinatraServerTest extends HttpServerTest<HttpServer> implements AgentTestTrait {
  private static final Duration TIMEOUT = Duration.fromSeconds(5)
  private static final long STARTUP_TIMEOUT = 40 * 1000

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
  boolean testPathParam() {
    true
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "FinatraController"
      kind INTERNAL
      childOf(parent as SpanData)
      // Finatra doesn't propagate the stack trace or exception to the instrumentation
      // so the normal errorAttributes() method can't be used
      errored false
      attributes {
      }
    }
  }
}
