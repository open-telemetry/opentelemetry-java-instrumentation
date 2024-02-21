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
import io.opentelemetry.instrumentation.spring.resources.SpringBootServiceNameDetector;
import io.opentelemetry.instrumentation.spring.resources.SpringBootServiceVersionDetector;
import io.opentelemetry.sdk.autoconfigure.internal.EnvironmentResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OtelResourceProperties.class})
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "otel.springboot.resource", name = "enabled", matchIfMissing = true)
public class OtelResourceAutoConfiguration {

  @Bean
  public ResourceProvider otelEnvironmentResourceProvider() {
    return new EnvironmentResourceProvider();
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
  @ConditionalOnClass(SpringBootServiceNameDetector.class)
  public ResourceProvider otelSpringBootServiceNameResourceProvider() {
    return new SpringBootServiceNameDetector();
  }

  @Bean
  @ConditionalOnClass(SpringBootServiceVersionDetector.class)
  public ResourceProvider otelSpringBootServiceVersionResourceProvider() {
    return new SpringBootServiceVersionDetector();
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
