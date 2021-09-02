/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

import static org.eclipse.jetty.util.resource.Resource.newResource

class JaxRsJettyHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath("/")
    // set up test application
    webAppContext.setBaseResource(newResource("src/test/webapp"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    "/rest-app"
  }
}