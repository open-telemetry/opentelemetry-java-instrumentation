/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.jaxrs.v2_0.JaxRsHttpServerTest;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestApplication;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.undertow.Undertow;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResteasyHttpServerTest extends JaxRsHttpServerTest<UndertowJaxrsServer> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected UndertowJaxrsServer setupServer() {
    UndertowJaxrsServer server = new UndertowJaxrsServer();
    server.deploy(JaxRsTestApplication.class, getContextPath());
    server.start(Undertow.builder().addHttpListener(port, "localhost"));

    return server;
  }

  @Override
  protected void stopServer(UndertowJaxrsServer server) {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setContextPath("/resteasy-context");
    options.setResponseCodeOnNonStandardHttpMethod(500);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + "/*";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }

  // resteasy 3.0.x does not support JAX-RS 2.1
  @Override
  protected boolean shouldTestCompletableStageAsync() {
    return false;
  }
}
