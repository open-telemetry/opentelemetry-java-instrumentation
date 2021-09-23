/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.apache.cxf.endpoint.Server
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

class CxfFilterTest extends JaxRsFilterTest implements HttpServerTestTrait<Server> {

  @Override
  boolean testAbortPrematch() {
    false
  }

  @Override
  boolean runsOnServer() {
    true
  }

  @Override
  Server startServer(int port) {
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean()
    serverFactory.setProviders([simpleRequestFilter, prematchRequestFilter])
    serverFactory.setResourceClasses([Test1, Test2, Test3])
    serverFactory.setAddress(buildAddress().toString())

    def server = serverFactory.create()
    server.start()

    return server
  }

  @Override
  void stopServer(Server httpServer) {
    httpServer.stop()
  }

  @Override
  def makeRequest(String path) {
    AggregatedHttpResponse response = client.post(address.resolve(path).toString(), "").aggregate().join()

    return [response.contentUtf8(), response.status().code()]
  }
}