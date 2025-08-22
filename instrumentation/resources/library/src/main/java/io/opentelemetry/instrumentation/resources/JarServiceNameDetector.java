/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.resources.internal.JarServiceNameResourceExtractor;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.Map;

/**
 * A {@link ResourceProvider} that will attempt to detect the <code>service.name</code> from the *
 * main jar file name.
 */
@AutoService(ResourceProvider.class)
public final class JarServiceNameDetector implements ConditionalResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    return new JarServiceNameResourceExtractor().extract();
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    String serviceName = config.getString("otel.service.name");
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    return serviceName == null
        && !resourceAttributes.containsKey(ServiceAttributes.SERVICE_NAME.getKey())
        && "unknown_service:java".equals(existing.getAttribute(ServiceAttributes.SERVICE_NAME));
  }

  @Override
  public int order() {
    // make it run later than the SpringBootServiceNameDetector
    return 1000;
  }
}
