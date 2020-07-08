/*
 * Copyright The OpenTelemetry Authors
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

import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Retry

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.SERVER

abstract class AkkaHttpServerInstrumentationTest extends HttpServerTest<Object> {

  @Override
  boolean testExceptionBody() {
    false
  }

// FIXME: This doesn't work because we don't support bindAndHandle.
//  @Override
//  def startServer(int port) {
//    AkkaHttpTestWebServer.start(port)
//  }
//
//  @Override
//  void stopServer(Object ignore) {
//    AkkaHttpTestWebServer.stop()
//  }

  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName(method, endpoint)
      spanKind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      attributes {
        "${SemanticAttributes.HTTP_URL.key()}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" endpoint.status
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$MoreAttributes.HTTP_QUERY" endpoint.query
        }
      }
    }
  }

  @Override
  String expectedOperationName(String method, ServerEndpoint endpoint) {
    return "akka.request"
  }
}

@Retry
class AkkaHttpServerInstrumentationTestSync extends AkkaHttpServerInstrumentationTest {
  @Override
  def startServer(int port) {
    AkkaHttpTestSyncWebServer.start(port)
  }

  @Override
  void stopServer(Object ignore) {
    AkkaHttpTestSyncWebServer.stop()
  }
}

@Retry
class AkkaHttpServerInstrumentationTestAsync extends AkkaHttpServerInstrumentationTest {
  @Override
  def startServer(int port) {
    AkkaHttpTestAsyncWebServer.start(port)
  }

  @Override
  void stopServer(Object ignore) {
    AkkaHttpTestAsyncWebServer.stop()
  }
}
