/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.util.Map;

/**
 * Detects <code>service.name</code> and <code>service.version</code> from Spring Boot's <code>
 * build-info.properties</code> file.
 */
@AutoService(ResourceProvider.class)
public class SpringBootBuildInfoServiceNameDetector extends SpringBootBuildInfoDetector {

  public SpringBootBuildInfoServiceNameDetector() {
    this(new SystemHelper());
  }

  // Exists for testing
  SpringBootBuildInfoServiceNameDetector(SystemHelper system) {
    super(ResourceAttributes.SERVICE_NAME, "build.name", system);
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    String serviceName = config.getString("otel.service.name");
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    return serviceName == null
        && !resourceAttributes.containsKey(ResourceAttributes.SERVICE_NAME.getKey())
        && "unknown_service:java".equals(existing.getAttribute(ResourceAttributes.SERVICE_NAME));
  }
}
