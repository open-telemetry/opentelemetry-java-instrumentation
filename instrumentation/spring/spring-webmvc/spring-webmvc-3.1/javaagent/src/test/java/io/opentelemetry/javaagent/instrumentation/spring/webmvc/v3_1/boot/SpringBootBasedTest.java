/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.webmvc.v3_1.boot.AbstractSpringBootBasedTest;
import io.opentelemetry.instrumentation.spring.webmvc.v3_1.boot.AppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

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
    Map<String, Object> properties = new HashMap<>();
    properties.put("server.port", port);
    properties.put("server.context-path", getContextPath());
    properties.put("server.servlet.contextPath", getContextPath());
    properties.put("server.error.include-message", "always");
    app.setDefaultProperties(properties);
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
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps() ? 500 : 200);
    options.setExpectedException(new RuntimeException(EXCEPTION.getBody()));
  }

  @Override
  protected boolean shouldTestDeferredResult() {
    // older versions of Spring Boot don't properly propagate context to async calls,
    // resulting in a separate trace instead of a single trace
    return testLatestDeps();
  }

  @Test
  void handlerMappingFilterResourceAvailable() {
    assertThat(
            getClass()
                .getClassLoader()
                .getResource(
                    "io/opentelemetry/javaagent/instrumentation/spring/webmvc/v3_1/OpenTelemetryHandlerMappingFilter.class"))
        .isNotNull();
  }

  @Test
  void handlerMappingFilterApplicationVisible() throws ClassNotFoundException {
    Class<?> filterClass = context.getBean("otelAutoDispatcherFilter").getClass();
    assertThat(filterClass.getName())
        .isEqualTo(
            "io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.OpenTelemetryHandlerMappingFilter");
    assertThat(context.getClassLoader().loadClass(filterClass.getName())).isSameAs(filterClass);
    assertThat(filterClass.getClassLoader()).isNotSameAs(DispatcherServlet.class.getClassLoader());
  }
}
