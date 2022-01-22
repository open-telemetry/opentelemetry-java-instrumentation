/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest

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
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    []
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
