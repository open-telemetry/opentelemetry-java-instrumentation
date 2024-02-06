/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;

@SuppressWarnings("deprecation") // old otel.springboot.resource support
public class SpringResourceProvider implements ResourceProvider {

  private final OtelSpringResourceProperties otelSpringResourceProperties;
  private final OtelResourceProperties otelResourceProperties;

  public SpringResourceProvider(
      OtelSpringResourceProperties otelSpringResourceProperties,
      OtelResourceProperties otelResourceProperties) {
    this.otelSpringResourceProperties = otelSpringResourceProperties;
    this.otelResourceProperties = otelResourceProperties;
  }

  @Override
  public Resource createResource(ConfigProperties configProperties) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    String springApplicationName = configProperties.getString("spring.application.name");
    if (springApplicationName != null) {
      attributesBuilder.put(ResourceAttributes.SERVICE_NAME, springApplicationName);
    }
    otelSpringResourceProperties.getAttributes().forEach(attributesBuilder::put);
    otelResourceProperties.getAttributes().forEach(attributesBuilder::put);
    String applicationName = configProperties.getString("otel.service.name");
    if (applicationName != null) {
      attributesBuilder.put(ResourceAttributes.SERVICE_NAME, applicationName);
    }
    return Resource.create(attributesBuilder.build());
  }
}
