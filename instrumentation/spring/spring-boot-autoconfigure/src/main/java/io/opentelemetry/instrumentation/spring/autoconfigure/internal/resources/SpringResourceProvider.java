/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SpringResourceProvider implements ResourceProvider {

  private final Optional<BuildProperties> buildProperties;

  public SpringResourceProvider(Optional<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Override
  public Resource createResource(ConfigProperties configProperties) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    buildProperties
        .map(BuildProperties::getName)
        .ifPresent(v -> attributesBuilder.put(SERVICE_NAME, v));

    attributesBuilder.put(SERVICE_NAME, configProperties.getString("spring.application.name"));

    buildProperties
        .map(BuildProperties::getVersion)
        .ifPresent(v -> attributesBuilder.put(SERVICE_VERSION, v));

    return Resource.create(attributesBuilder.build());
  }
}
