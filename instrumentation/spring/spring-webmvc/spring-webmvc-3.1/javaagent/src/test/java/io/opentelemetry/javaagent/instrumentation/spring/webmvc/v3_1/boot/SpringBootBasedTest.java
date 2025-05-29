/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.spring.webmvc.boot.AbstractSpringBootBasedTest;
import io.opentelemetry.instrumentation.spring.webmvc.boot.AppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private ConfigurableApplicationContext context;

  @Override
  protected ConfigurableApplicationContext context() {
    return context;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(AppConfig.class, securityConfigClass());
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
    context = app.run();
    return context;
  }

  @Override
  public Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(
        Boolean.getBoolean("testLatestDeps") ? 500 : 200);
    options.setExpectedException(new RuntimeException(EXCEPTION.getBody()));
  }
}
