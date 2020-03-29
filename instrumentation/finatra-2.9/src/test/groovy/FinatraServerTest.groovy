/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.twitter.finatra.http.HttpServer
import com.twitter.util.Await
import com.twitter.util.Closable
import com.twitter.util.Duration
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData

import java.util.concurrent.TimeoutException

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER

class FinatraServerTest extends HttpServerTest<HttpServer> {
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
  void stopServer(HttpServer httpServer) {
    Await.ready(httpServer.close(), TIMEOUT)
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    def errorEndpoint = endpoint == EXCEPTION || endpoint == ERROR
    trace.span(index) {
      operationName "FinatraController"
      spanKind INTERNAL
      errored errorEndpoint
      childOf(parent as SpanData)
      tags {
        // Finatra doesn't propagate the stack trace or exception to the instrumentation
        // so the normal errorTags() method can't be used
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.resolvePath(address).path
      spanKind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$MoreTags.NET_PEER_PORT" Long
        "$MoreTags.NET_PEER_IP" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }
}
