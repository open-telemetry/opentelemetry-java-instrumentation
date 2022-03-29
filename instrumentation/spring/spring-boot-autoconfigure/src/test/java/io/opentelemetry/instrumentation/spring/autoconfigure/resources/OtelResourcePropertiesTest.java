/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class OtelResourcePropertiesTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("otel.springboot.resource.enabled=true")
          .withConfiguration(AutoConfigurations.of(OtelResourceAutoConfiguration.class));

  @Test
  @DisplayName("when attributes are SET should set OtelResourceProperties with given attributes")
  void hasAttributes() {

    this.contextRunner
        .withPropertyValues(
            "otel.springboot.resource.attributes.environment=dev",
            "otel.springboot.resource.attributes.xyz=foo",
            "otel.springboot.resource.attributes.service.name=backend-name",
            "otel.springboot.resource.attributes.service.instance.id=id-example")
        .run(
            context -> {
              OtelResourceProperties propertiesBean = context.getBean(OtelResourceProperties.class);

              assertThat(propertiesBean.getAttributes())
                  .contains(
                      entry("environment", "dev"),
                      entry("xyz", "foo"),
                      entry("service.name", "backend-name"),
                      entry("service.instance.id", "id-example"));
            });
  }

  @Test
  @DisplayName("when attributes are DEFAULT should set OtelResourceProperties to default values")
  void hasDefaultTypes() {

    this.contextRunner.run(
        context ->
            assertThat(context.getBean(OtelResourceProperties.class).getAttributes()).isEmpty());
  }
}
