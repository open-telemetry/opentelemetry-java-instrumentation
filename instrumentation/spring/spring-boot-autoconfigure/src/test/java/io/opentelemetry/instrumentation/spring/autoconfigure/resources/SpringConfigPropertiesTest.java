/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.PropagationProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.SpringConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class SpringConfigPropertiesTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName("when map is set in properties in a row it should be available in config")
  void shouldInitializeAttributesByMapInArow() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues(
            "otel.metrics.exporter=none", // to suppress confusing error log
            "otel.logs.exporter=none",
            "otel.traces.exporter=none",
            "otel.resource.attributes.environment=dev",
            "otel.resource.attributes.xyz=foo",
            "otel.resource.attributes.service.instance.id=id-example")
        .run(
            context -> {
              Environment env = context.getBean("environment", Environment.class);
              Map<String, String> fallback = new HashMap<>();
              fallback.put("fallback", "fallbackVal");
              fallback.put("otel.resource.attributes", "foo=fallback");

              SpringConfigProperties config =
                  new SpringConfigProperties(
                      env,
                      new SpelExpressionParser(),
                      context.getBean(OtlpExporterProperties.class),
                      context.getBean(OtelResourceProperties.class),
                      context.getBean(PropagationProperties.class),
                      DefaultConfigProperties.createFromMap(fallback));

              assertThat(config.getMap("otel.resource.attributes"))
                  .contains(
                      entry("environment", "dev"),
                      entry("xyz", "foo"),
                      entry("service.instance.id", "id-example"),
                      entry("foo", "fallback"));

              assertThat(config.getString("fallback")).isEqualTo("fallbackVal");
            });
  }
}
