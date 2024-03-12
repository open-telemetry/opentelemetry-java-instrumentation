/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.propagators.PropagationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class SpringConfigPropertiesTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName("when map is set in properties in a row it should be available in config")
  void shouldInitializeAttributesByMapInArow() {
    this.contextRunner
        .withPropertyValues(
            "otel.springboot.test.map={'environment':'dev','xyz':'foo','service.instance.id':'id-example'}")
        .run(
            context -> {
              Environment env = context.getBean("environment", Environment.class);
              SpringConfigProperties config =
                  new SpringConfigProperties(
                      env,
                      new SpelExpressionParser(),
                      new OtlpExporterProperties(),
                      new OtelResourceProperties(),
                      new PropagationProperties());

              assertThat(config.getMap("otel.springboot.test.map"))
                  .contains(
                      entry("environment", "dev"),
                      entry("xyz", "foo"),
                      entry("service.instance.id", "id-example"));
            });
  }
}
