/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.MapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SpringComponentLoader;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.PropagationProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.SpringConfigProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.DistroVersionResourceProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.SpringResourceProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Create {@link io.opentelemetry.api.OpenTelemetry} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
@EnableConfigurationProperties({
  OtlpExporterProperties.class,
  OtelResourceProperties.class,
  PropagationProperties.class
})
public class OpenTelemetryAutoConfiguration {

  public OpenTelemetryAutoConfiguration() {}

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
  public static class OpenTelemetrySdkConfig {

    @Bean
    @ConfigurationPropertiesBinding
    public MapConverter mapConverter() {
      // needed for otlp exporter headers and OtelResourceProperties
      return new MapConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    ConfigProperties configProperties(
        Environment env,
        OtlpExporterProperties otlpExporterProperties,
        OtelResourceProperties resourceProperties,
        PropagationProperties propagationProperties) {
      return new SpringConfigProperties(
          env,
          new SpelExpressionParser(),
          otlpExporterProperties,
          resourceProperties,
          propagationProperties);
    }

    @Bean
    public SpringComponentLoader springComponentLoader(ApplicationContext applicationContext) {
      return new SpringComponentLoader(applicationContext);
    }

    @Bean
    public ResourceProvider otelSpringResourceProvider() {
      return new SpringResourceProvider();
    }

    @Bean
    public ResourceProvider otelDistroVersionResourceProvider() {
      return new DistroVersionResourceProvider();
    }

    @Bean
    // If you change the bean name, also change it in the OpenTelemetryJdbcDriverAutoConfiguration
    // class
    public OpenTelemetry openTelemetry(
        SpringComponentLoader componentLoader,
        ConfigProperties configProperties,
        ObjectProvider<OpenTelemetryInjector> openTelemetryConsumerProvider) {

      OpenTelemetry openTelemetry =
          AutoConfiguredOpenTelemetrySdk.builder()
              .setConfig(configProperties)
              .setComponentLoader(componentLoader)
              .build()
              .getOpenTelemetrySdk();

      openTelemetryConsumerProvider.forEach(consumer -> consumer.accept(openTelemetry));

      return openTelemetry;
    }
  }

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "true")
  public static class DisabledOpenTelemetrySdkConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
      return OpenTelemetry.noop();
    }
  }
}
