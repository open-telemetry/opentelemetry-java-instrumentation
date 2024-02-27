/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SpringResourceProviderTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("otel.springboot.resource.enabled=true")
          .withConfiguration(
              AutoConfigurations.of(
                  OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class));

  @Test
  @DisplayName("when attributes are SET should set OtelResourceProperties with given attributes")
  void hasAttributes() {

    this.contextRunner.run(
        context -> {
          ResourceProvider resource =
              context.getBean("otelSpringResourceProvider", ResourceProvider.class);

          assertThat(
                  resource
                      .createResource(
                          DefaultConfigProperties.createFromMap(
                              ImmutableMap.of("spring.application.name", "backend")))
                      .getAttributes()
                      .asMap())
              .contains(entry(AttributeKey.stringKey("service.name"), "backend"));
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
