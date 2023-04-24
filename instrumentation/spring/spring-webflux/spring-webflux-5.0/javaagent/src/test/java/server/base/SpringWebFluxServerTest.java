/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NESTED_PATH;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

public abstract class SpringWebFluxServerTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {
  protected abstract Class<?> getApplicationClass();

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(getApplicationClass());
    app.setDefaultProperties(
        ImmutableMap.of(
            "server.port",
            port,
            "server.context-path",
            getContextPath(),
            "server.servlet.contextPath",
            getContextPath(),
            "server.error.include-message",
            "always"));
    return app.run();
  }

  @Override
  public void stopServer(ConfigurableApplicationContext configurableApplicationContext) {
    configurableApplicationContext.close();
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint) {
    if (endpoint.equals(PATH_PARAM)) {
      return getContextPath() + "/path/{id}/param";
    } else if (endpoint.equals(NOT_FOUND)) {
      return "/**";
    } else if (endpoint.equals(NESTED_PATH)) {
      return "/nestedPath/hello/world";
    }
    return super.expectedHttpRoute(endpoint);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestPathParam(true);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    options.setHasHandlerSpan(unused -> true);
  }
}
