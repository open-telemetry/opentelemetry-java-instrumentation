/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean
import org.apache.cxf.endpoint.Server

class CxfHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  Server startServer(int port) {
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean()
    def application = new JaxRsTestApplication()
    serverFactory.setApplication(application)
    serverFactory.setResourceClasses(new ArrayList<Class<?>>(application.getClasses()))
    serverFactory.setProvider(new ExceptionMapper<Exception>() {
      @Override
      Response toResponse(Exception exception) {
        return Response.status(500)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity(exception.getMessage())
          .build()
      }
    })
    serverFactory.setAddress(buildAddress().toString())

    def server = serverFactory.create()
    server.start()

    return server
  }

  @Override
  void stopServer(Server httpServer) {
    httpServer.stop()
  }
}