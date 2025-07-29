/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class SpringWebFluxServerTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  protected static final ServerEndpoint NESTED_PATH =
      new ServerEndpoint("NESTED_PATH", "nestedPath/hello/world", 200, "nested path");

  protected abstract Class<?> getApplicationClass();

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(getApplicationClass());
    Map<String, Object> properties = new HashMap<>();
    properties.put("server.port", port);
    properties.put("server.context-path", getContextPath());
    properties.put("server.servlet.contextPath", getContextPath());
    properties.put("server.error.include-message", "always");
    app.setDefaultProperties(properties);
    return app.run();
  }

  @Override
  public void stopServer(ConfigurableApplicationContext configurableApplicationContext) {
    configurableApplicationContext.close();
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (endpoint.equals(PATH_PARAM)) {
      return getContextPath() + "/path/{id}/param";
    } else if (endpoint.equals(NOT_FOUND)) {
      return "/**";
    } else if (endpoint.equals(NESTED_PATH)) {
      return "/nestedPath/hello/world";
    }
    return super.expectedHttpRoute(endpoint, method);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestPathParam(true);
    options.setHasHandlerSpan(unused -> true);
  }
}
