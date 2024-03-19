/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot;

import boot.AbstractSpringBootBasedTest;
import boot.AppConfig;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

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
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps ? 500 : 200);
  }
}
