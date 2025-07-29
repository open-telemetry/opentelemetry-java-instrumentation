/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.jaxrs.v2_0.JaxRsHttpServerTest;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestApplication;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import java.util.ArrayList;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.jupiter.api.extension.RegisterExtension;

class CxfHttpServerTest extends JaxRsHttpServerTest<Server> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() {
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    JaxRsTestApplication application = new JaxRsTestApplication();
    serverFactory.setApplication(application);
    serverFactory.setResourceClasses(new ArrayList<>(application.getClasses()));
    serverFactory.setProvider(
        (ExceptionMapper<Exception>)
            exception ->
                Response.status(500)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(exception.getMessage())
                    .build());

    serverFactory.setAddress(buildAddress().toString());

    Server server = serverFactory.create();
    server.start();

    return server;
  }

  @Override
  protected void stopServer(Server httpServer) {
    httpServer.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setResponseCodeOnNonStandardHttpMethod(500);
  }
}
