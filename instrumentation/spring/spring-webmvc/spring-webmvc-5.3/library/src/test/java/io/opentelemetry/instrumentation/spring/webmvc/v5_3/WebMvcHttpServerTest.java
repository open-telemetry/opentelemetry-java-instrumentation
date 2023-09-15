/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;

class WebMvcHttpServerTest extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  private static final String CONTEXT_PATH = "/test";

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    return TestWebSpringBootApp.start(port, CONTEXT_PATH);
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext applicationContext) {
    applicationContext.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath(CONTEXT_PATH);
    options.setTestPathParam(true);
    // servlet filters don't capture exceptions thrown in controllers
    options.setTestException(false);

    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint == ServerEndpoint.PATH_PARAM) {
            return CONTEXT_PATH + "/path/{id}/param";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }
}
