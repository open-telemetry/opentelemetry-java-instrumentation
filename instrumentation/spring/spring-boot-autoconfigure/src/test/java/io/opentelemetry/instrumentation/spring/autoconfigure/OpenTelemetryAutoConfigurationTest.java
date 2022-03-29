/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.OtelResourceAutoConfiguration;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class OpenTelemetryAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customOpenTelemetry() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("customOpenTelemetry")
                    .doesNotHaveBean("openTelemetry")
                    .doesNotHaveBean("sdkTracerProvider"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeTracerProviderAndOpenTelemetry() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(context -> assertThat(context).hasBean("openTelemetry").hasBean("sdkTracerProvider"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean but TracerProvider should initialize openTelemetry")
  void initializeOpenTelemetry() {
    this.contextRunner
        .withBean(
            "customTracerProvider",
            SdkTracerProvider.class,
            () -> SdkTracerProvider.builder().build())
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("openTelemetry")
                    .hasBean("customTracerProvider")
                    .doesNotHaveBean("sdkTracerProvider"));
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
        .withPropertyValues("otel.springboot.resource.attributes.service.name=otel-name-backend")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(SERVICE_NAME)).isEqualTo("otel-name-backend");
            });
  }

  @Test
  @DisplayName("when otel attributes are set in properties they should be put in resource")
  void shouldInitializeAttributes() {
    this.contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .withPropertyValues(
            "otel.springboot.resource.attributes.xyz=foo",
            "otel.springboot.resource.attributes.environment=dev",
            "otel.springboot.resource.attributes.service.instance.id=id-example")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(AttributeKey.stringKey("environment")))
                  .isEqualTo("dev");
              assertThat(otelResource.getAttribute(AttributeKey.stringKey("xyz"))).isEqualTo("foo");
              assertThat(otelResource.getAttribute(AttributeKey.stringKey("service.instance.id")))
                  .isEqualTo("id-example");
            });
  }
}
