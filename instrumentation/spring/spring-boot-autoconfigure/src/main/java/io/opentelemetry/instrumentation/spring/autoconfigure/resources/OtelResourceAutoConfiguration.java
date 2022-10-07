/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OtelResourceProperties.class)
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "otel.springboot.resource", name = "enabled", matchIfMissing = true)
@SuppressWarnings("deprecation") // TODO: io.opentelemetry.instrumentation:opentelemetry-resources
public class OtelResourceAutoConfiguration {

  @Bean
  public ResourceProvider otelResourceProvider(OtelResourceProperties otelResourceProperties) {
    return new SpringResourceProvider(otelResourceProperties);
  }

  @Bean
  @ConditionalOnClass(io.opentelemetry.sdk.extension.resources.OsResource.class)
  public ResourceProvider otelOsResourceProvider() {
    return new io.opentelemetry.sdk.extension.resources.OsResourceProvider();
  }

  @Bean
  @ConditionalOnClass(io.opentelemetry.sdk.extension.resources.ProcessResource.class)
  public ResourceProvider otelProcessResourceProvider() {
    return new io.opentelemetry.sdk.extension.resources.ProcessResourceProvider();
  }

  @Bean
  @ConditionalOnClass(io.opentelemetry.sdk.extension.resources.ProcessRuntimeResource.class)
  public ResourceProvider otelProcessRuntimeResourceProvider() {
    return new io.opentelemetry.sdk.extension.resources.ProcessRuntimeResourceProvider();
  }

  @Bean
  @ConditionalOnClass(io.opentelemetry.sdk.extension.resources.HostResource.class)
  public ResourceProvider otelHostResourceProvider() {
    return new io.opentelemetry.sdk.extension.resources.HostResourceProvider();
  }

  @Bean
  @ConditionalOnClass(io.opentelemetry.sdk.extension.resources.ContainerResource.class)
  public ResourceProvider otelContainerResourceProvider() {
    return new io.opentelemetry.sdk.extension.resources.ContainerResourceProvider();
  }
}
