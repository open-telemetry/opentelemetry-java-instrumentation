/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class DeclarativeDebugSystemPropertySmokeTest {
  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation/development.java.spring_starter.debug",
      value = "true")
  void debugSystemPropertyLogsSpans(CapturedOutput output) {
    // SetSystemProperty sets a JVM property before Spring starts and restores it after the test.
    // This file has no debug setting or console exporter, so only the system property enables it.
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(SmokeTestConfiguration.class)
            .web(WebApplicationType.NONE)
            .run("--spring.config.location=classpath:debug-system-property.yaml");
    cleanup.deferCleanup(context);

    context
        .getBean(OpenTelemetry.class)
        .getTracer("debug-system-property-smoke-test")
        .spanBuilder("system-property-debug-span")
        .startSpan()
        .end();

    assertThat(output.getAll()).containsOnlyOnce("system-property-debug-span");
  }

  @TestConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
      })
  static class SmokeTestConfiguration {}
}
