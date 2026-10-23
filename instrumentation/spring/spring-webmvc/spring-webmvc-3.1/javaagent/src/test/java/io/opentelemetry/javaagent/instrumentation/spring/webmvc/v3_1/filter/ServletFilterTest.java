/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.filter;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.instrumentation.spring.webmvc.v3_1.filter.AbstractServletFilterTest;
import io.opentelemetry.instrumentation.spring.webmvc.v3_1.filter.FilteredAppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AgentHttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.boot.SecurityConfig;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class ServletFilterTest extends AbstractServletFilterTest {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      AgentHttpServerInstrumentationExtension.create();

  @Override
  protected Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected Class<?> filterConfigClass() {
    return ServletFilterConfig.class;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app =
        new SpringApplication(FilteredAppConfig.class, securityConfigClass(), filterConfigClass());
    Map<String, Object> properties = new HashMap<>();
    properties.put("server.port", port);
    properties.put("server.error.include-message", "always");
    app.setDefaultProperties(properties);
    return app.run();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps() ? 500 : 200);
  }
}
