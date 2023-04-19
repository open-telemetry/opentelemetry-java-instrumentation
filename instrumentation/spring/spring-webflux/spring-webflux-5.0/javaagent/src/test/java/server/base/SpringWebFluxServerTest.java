/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NESTED_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
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
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param";
      case NOT_FOUND:
        return "/**";
      case NESTED_PATH:
        return "/nestedPath/hello/world";
      default:
        return super.expectedHttpRoute(endpoint);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestPathParam(true);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    options.setHasHandlerSpan(unused -> true);
  }

  @Test
  void nestedPath() {
    assumeTrue(Boolean.getBoolean("testLatestDeps"));

    String method = "GET";
    AggregatedHttpRequest request = request(NESTED_PATH, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();
    assertThat(response.status().code()).isEqualTo(NESTED_PATH.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(NESTED_PATH.getBody());
    assertResponseHasCustomizedHeaders(response, NESTED_PATH, null);

    assertTheTraces(1, null, null, null, method, NESTED_PATH, response);
  }
}
