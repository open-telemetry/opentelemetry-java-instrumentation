/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.twitter.app.lifecycle.Event
import com.twitter.app.lifecycle.Observer
import com.twitter.finatra.http.HttpServer
import com.twitter.util.Await
import com.twitter.util.Closable
import com.twitter.util.Duration
import com.twitter.util.Promise
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class FinatraServerLatestTest extends HttpServerTest<HttpServer> implements AgentTestTrait {
  private static final Duration TIMEOUT = Duration.fromSeconds(5)
  private static final Duration STARTUP_TIMEOUT = Duration.fromSeconds(20)

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

    Promise<Boolean> startupPromise = new Promise<>()

    testServer.withObserver(new Observer() {
      @Override
      void onSuccess(Event event) {
        if (event == testServer.startupCompletionEvent()) {
          startupPromise.setValue(true)
        }
      }

      void onEntry(Event event) {

      }

      @Override
      void onFailure(Event stage, Throwable throwable) {
        if (stage != Event.Close$.MODULE$) {
          startupPromise.setException(throwable)
        }
      }
    })

    Await.result(startupPromise, STARTUP_TIMEOUT)

    return testServer
  }

  @Override
  void stopServer(HttpServer httpServer) {
    Await.ready(httpServer.close(), TIMEOUT)
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    endpoint != NOT_FOUND
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == NOT_FOUND ? "HTTP GET" : super.expectedServerSpanName(endpoint)
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "FinatraController"
      kind INTERNAL
      childOf(parent as SpanData)
      // Finatra doesn't propagate the stack trace or exception to the instrumentation
      // so the normal errorAttributes() method can't be used
      attributes {
      }
    }
  }
}
