/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.jaxrs.v2_0.JaxRsFilterTest;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.Resource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class CxfFilterTest extends JaxRsFilterTest<Server> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  void setUp() {
    startServer();
  }

  @AfterAll
  void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Server setupServer() {
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    serverFactory.setProviders(asList(simpleRequestFilter, prematchRequestFilter));
    serverFactory.setResourceClasses(
        asList(Resource.Test1.class, Resource.Test2.class, Resource.Test3.class));
    serverFactory.setAddress(buildAddress().toString());

    Server server = serverFactory.create();
    server.start();

    return server;
  }

  @Override
  protected void stopServer(Server server) {
    server.stop();
  }

  @Override
  protected String getContextPath() {
    return "";
  }

  @Override
  protected TestResponse makeRequest(String url) {
    AggregatedHttpResponse response =
        client.post(address.resolve(url).toString(), "").aggregate().join();
    return new TestResponse(response.contentUtf8(), response.status().code());
  }

  @Override
  protected boolean testAbortPrematch() {
    return false;
  }

  @Override
  protected boolean runsOnServer() {
    return true;
  }
}
