/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class OtelResourceAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class));

  @Test
  @DisplayName("when otel service name is set it should be set as service name attribute")
  void shouldDetermineServiceNameByOtelServiceName() {
    this.contextRunner
        .withPropertyValues(
            "otel.springboot.resource.attributes.service.name=otel-name-backend",
            "otel.springboot.resource.enabled=true")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("otel-name-backend");
            });
  }

  @Test
  @DisplayName(
      "when otel.springboot.resource.enabled is not specified configuration should be initialized")
  void shouldInitAutoConfigurationByDefault() {
    this.contextRunner
        .withPropertyValues("otel.springboot.resource.attributes.service.name=otel-name-backend")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("otel-name-backend");
            });
  }

  @Test
  @DisplayName(
      "when otel.springboot.resource.enabled is set to false configuration should NOT be initialized")
  void shouldNotInitAutoConfiguration() {
    this.contextRunner
        .withPropertyValues(
            "otel.springboot.resource.attributes.service.name=otel-name-backend",
            "otel.springboot.resource.enabled=false")
        .run(context -> assertThat(context.containsBean("otelResourceProvider")).isFalse());
  }

  @Test
  @DisplayName("when otel attributes are set in properties they should be put in resource")
  void shouldInitializeAttributes() {
    this.contextRunner
        .withPropertyValues(
            "otel.springboot.resource.attributes.xyz=foo",
            "otel.springboot.resource.attributes.environment=dev",
            "otel.springboot.resource.enabled=true")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(AttributeKey.stringKey("environment")))
                  .isEqualTo("dev");
              assertThat(otelResource.getAttribute(AttributeKey.stringKey("xyz"))).isEqualTo("foo");
            });
  }
}
