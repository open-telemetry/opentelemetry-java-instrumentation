/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

abstract class AkkaHttpServerInstrumentationTest extends HttpServerTest<Object> implements AgentTestTrait {

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

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [SemanticAttributes.HTTP_ROUTE ]
  }


  String expectedServerSpanName(ServerEndpoint endpoint) {
    StringBuilder result = new StringBuilder()
    String path = endpoint.resolvePath(address).path

    // strip of preceding slash for split()
    if (path.startsWith("/")) {
      path = path.substring(1)
      result.append("/")
    }
    String[] segments = path.split("/")

    // limit segments to 2
    for (int i = 0; i < segments.length && i < 2; i++) {
      result.append(segments[i]).append("/")
    }

    if (result.length() <= 0) {
      return "AKKA HTTP"
    }
    // strip trailing slash
    result.setLength(result.length()-1)
    return result.toString()
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes
  }
}

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
