/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.ContainerResourceProvider;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.instrumentation.resources.HostResourceProvider;
import io.opentelemetry.instrumentation.resources.OsResource;
import io.opentelemetry.instrumentation.resources.OsResourceProvider;
import io.opentelemetry.instrumentation.resources.ProcessResource;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResource;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResourceProvider;
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
public class OtelResourceAutoConfiguration {

  @Bean
  public ResourceProvider otelResourceProvider(OtelResourceProperties otelResourceProperties) {
    return new SpringResourceProvider(otelResourceProperties);
  }

  @Bean
  @ConditionalOnClass(OsResource.class)
  public ResourceProvider otelOsResourceProvider() {
    return new OsResourceProvider();
  }

  @Bean
  @ConditionalOnClass(ProcessResource.class)
  public ResourceProvider otelProcessResourceProvider() {
    return new ProcessResourceProvider();
  }

  @Bean
  @ConditionalOnClass(ProcessRuntimeResource.class)
  public ResourceProvider otelProcessRuntimeResourceProvider() {
    return new ProcessRuntimeResourceProvider();
  }

  @Bean
  @ConditionalOnClass(HostResource.class)
  public ResourceProvider otelHostResourceProvider() {
    return new HostResourceProvider();
  }

  @Bean
  @ConditionalOnClass(ContainerResource.class)
  public ResourceProvider otelContainerResourceProvider() {
    return new ContainerResourceProvider();
  }
}
