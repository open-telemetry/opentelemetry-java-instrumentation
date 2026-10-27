/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.vaadin.flow.server.Version;
import com.vaadin.flow.spring.annotation.EnableVaadin;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class AbstractVaadinTest
    extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.vaadin-14.2";
  private static final boolean CONTROLLER_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.common.controller-telemetry.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @SpringBootApplication
  @EnableVaadin("test.vaadin")
  static class TestApplication {
    public TestApplication() {}

    static ConfigurableApplicationContext start(
        int port, String contextPath, boolean productionMode, boolean compatibilityMode) {
      SpringApplication app = new SpringApplication(TestApplication.class);
      Map<String, Object> properties = new HashMap<>();
      properties.put("server.port", port);
      properties.put("server.servlet.contextPath", contextPath);
      properties.put(
          "server.servlet.context-parameters.original.frontend.resources", productionMode);
      properties.put("server.error.include-message", "always");
      properties.put("vaadin.productionMode", productionMode);
      properties.put("vaadin.compatibilityMode", compatibilityMode);
      app.setDefaultProperties(properties);
      return app.run();
    }
  }

  @BeforeAll
  protected void setup() {
    startServer();
    cleanup.deferAfterAll(this::cleanupServer);
    address = URI.create("http://localhost:" + port + getContextPath() + "/");
  }

  protected boolean isProductionMode() {
    return true;
  }

  protected boolean isCompatibilityMode() {
    return false;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    File baseDir = new File("build/vaadin-" + Version.getFullVersion());
    if (!baseDir.mkdirs() && !baseDir.isDirectory()) {
      throw new IllegalStateException("Could not create directory: " + baseDir);
    }
    System.setProperty("vaadin.project.basedir", baseDir.getAbsolutePath());
    return TestApplication.start(port, getContextPath(), isProductionMode(), isCompatibilityMode());
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  public String getContextPath() {
    return "/xyz";
  }

  abstract void assertFirstRequest();

  protected static boolean controllerTelemetryEnabled() {
    return CONTROLLER_TELEMETRY_ENABLED;
  }

  protected static void assertNoControllerSpans(List<? extends SpanData> spans) {
    assertThat(spans)
        .noneMatch(
            span -> INSTRUMENTATION_SCOPE.equals(span.getInstrumentationScopeInfo().getName()));
  }
}
