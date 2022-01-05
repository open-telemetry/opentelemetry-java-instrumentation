/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.server.engine.ApplicationEngine
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import java.util.concurrent.TimeUnit

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND

class KtorHttpServerTest extends HttpServerTest<ApplicationEngine> implements LibraryTestTrait {

  @Override
  ApplicationEngine startServer(int port) {
    return TestServer.startServer(port, openTelemetry)
  }

  @Override
  void stopServer(ApplicationEngine server) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  // ktor does not have a controller lifecycle so the server span ends immediately when the response is sent, which is
  // before the controller span finishes.
  @Override
  boolean verifyServerSpanEndTime() {
    return false
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case NOT_FOUND:
        return "HTTP GET"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.NET_PEER_NAME,
      SemanticAttributes.NET_TRANSPORT
    ]
  }
}
