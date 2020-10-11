/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.undertow.Undertow
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer

class ResteasyHttpServerTest extends JaxRsHttpServerTest<UndertowJaxrsServer> {

  @Override
  UndertowJaxrsServer startServer(int port) {
    def server = new UndertowJaxrsServer()
    server.deploy(JaxRsTestApplication)
    server.start(Undertow.builder()
      .addHttpListener(port, "localhost"))
    return server
  }

  @Override
  void stopServer(UndertowJaxrsServer server) {
    server.stop()
  }
}