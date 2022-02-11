/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.extension.resources.ContainerResource;
import io.opentelemetry.sdk.extension.resources.HostResource;
import io.opentelemetry.sdk.extension.resources.OsResource;
import io.opentelemetry.sdk.extension.resources.ProcessResource;
import io.opentelemetry.sdk.extension.resources.ProcessRuntimeResource;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.function.Supplier;
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
  public Supplier<Resource> otelResourceProvider(OtelResourceProperties otelResourceProperties) {
    return () -> {
      AttributesBuilder attributesBuilder = Attributes.builder();
      for (Map.Entry<String, String> entry : otelResourceProperties.getAttributes().entrySet()) {
        attributesBuilder.put(entry.getKey(), entry.getValue());
      }
      Attributes attributes = attributesBuilder.build();
      return Resource.create(attributes);
    };
  }

  @Bean
  @ConditionalOnClass(OsResource.class)
  public Supplier<Resource> otelOsResourceProvider() {
    return OsResource::get;
  }

  @Bean
  @ConditionalOnClass(ProcessResource.class)
  public Supplier<Resource> otelProcessResourceProvider() {
    return ProcessResource::get;
  }

  @Bean
  @ConditionalOnClass(ProcessRuntimeResource.class)
  public Supplier<Resource> otelProcessRuntimeResourceProvider() {
    return ProcessRuntimeResource::get;
  }

  @Bean
  @ConditionalOnClass(HostResource.class)
  public Supplier<Resource> otelHostResourceProvider() {
    return HostResource::get;
  }

  @Bean
  @ConditionalOnClass(ContainerResource.class)
  public Supplier<Resource> otelContainerResource() {
    return ContainerResource::get;
  }
}
