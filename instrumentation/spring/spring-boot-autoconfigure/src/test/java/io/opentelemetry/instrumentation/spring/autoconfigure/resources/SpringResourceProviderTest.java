/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SpringResourceProviderTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none")
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

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
  @DisplayName(
      "when spring.application.name is set value should be passed to service name attribute")
  void shouldDetermineServiceNameBySpringApplicationName() {
    this.contextRunner
        .withPropertyValues("spring.application.name=myapp-backend")
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("myapp-backend");
            });
  }

  @Test
  @DisplayName(
      "when spring.application.name is set value should be passed to service name attribute")
  void shouldDetermineServiceNameAndVersionBySpringApplicationVersion() {
    Properties properties = new Properties();
    properties.put("name", "demo");
    properties.put("version", "0.3");
    this.contextRunner
        .withBean("buildProperties", BuildProperties.class, () -> new BuildProperties(properties))
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("demo");
              assertThat(otelResource.getAttribute(SERVICE_VERSION)).isEqualTo("0.3");
            });
  }

  @Test
  @DisplayName(
      "when spring application name and otel service name are not set service name should be default")
  void hasDefaultServiceName() {
    this.contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("unknown_service:java");
            });
  }

  @Test
  @DisplayName("when otel service name is set it should be set as service name attribute")
  void shouldDetermineServiceNameByOtelServiceName() {
    this.contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.resource.attributes.service.name=otel-name-backend")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("otel-name-backend");
            });
  }
}
