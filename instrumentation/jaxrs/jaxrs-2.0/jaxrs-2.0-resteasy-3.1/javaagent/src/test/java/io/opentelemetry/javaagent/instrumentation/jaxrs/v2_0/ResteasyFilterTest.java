/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.jaxrs.v2_0.JaxRsFilterTest;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.Resource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import javax.ws.rs.core.MediaType;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Registry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResteasyFilterTest extends JaxRsFilterTest<Void> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private SynchronousDispatcher dispatcher;

  @BeforeAll
  void setUp() {
    startServer();
  }

  @AfterAll
  void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Void setupServer() {
    // using implementation class SynchronousDispatcher instead of the Dispatcher interface because
    // the interface moves to a different package for the latest dep tests
    dispatcher = (SynchronousDispatcher) MockDispatcherFactory.createDispatcher();
    Registry registry = dispatcher.getRegistry();
    registry.addSingletonResource(new Resource.Test1());
    registry.addSingletonResource(new Resource.Test2());
    registry.addSingletonResource(new Resource.Test3());

    dispatcher.getProviderFactory().register(simpleRequestFilter);
    dispatcher.getProviderFactory().register(prematchRequestFilter);

    return null;
  }

  @Override
  protected void stopServer(Void server) {}

  @Override
  protected String getContextPath() {
    return "";
  }

  @Override
  protected TestResponse makeRequest(String url) throws Exception {
    MockHttpRequest request = MockHttpRequest.post(url);
    request.contentType(MediaType.TEXT_PLAIN_TYPE);
    request.content(new byte[0]);

    MockHttpResponse response = new MockHttpResponse();
    dispatcher.invoke(request, response);

    return new TestResponse(response.getContentAsString(), response.getStatus());
  }
}
