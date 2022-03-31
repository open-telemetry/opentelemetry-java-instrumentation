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
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.Map;

public class SpringResourceProvider implements ResourceProvider {

  private final OtelResourceProperties otelResourceProperties;

  public SpringResourceProvider(OtelResourceProperties otelResourceProperties) {
    this.otelResourceProperties = otelResourceProperties;
  }

  @Override
  public Resource createResource(ConfigProperties configProperties) {
    String applicationName = configProperties.getString("spring.application.name");
    Map<String, String> attributes = otelResourceProperties.getAttributes();
    AttributesBuilder attributesBuilder = Attributes.builder();
    attributes.forEach(attributesBuilder::put);
    return defaultResource(applicationName).merge(Resource.create(attributesBuilder.build()));
  }

  private static Resource defaultResource(String applicationName) {
    if (applicationName == null) {
      return Resource.getDefault();
    }
    return Resource.getDefault()
        .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, applicationName)));
  }
}
