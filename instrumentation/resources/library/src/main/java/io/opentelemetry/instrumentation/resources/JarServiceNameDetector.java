/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link ResourceProvider} that will attempt to detect the application name from the jar name.
 */
@AutoService(ResourceProvider.class)
public final class JarServiceNameDetector extends JarResourceDetector {

  @SuppressWarnings("unused") // SPI
  public JarServiceNameDetector() {
    this(
        ProcessArguments::getProcessArguments,
        System::getProperty,
        Files::isRegularFile,
        CACHE_LOOKUP);
  }

  // visible for tests
  JarServiceNameDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists,
      Function<Supplier<Optional<String>>, Optional<String>> jarNameCacheLookup) {
    super(getProcessHandleArguments, getSystemProperty, fileExists, jarNameCacheLookup);
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return getServiceNameAndVersion()
        .map(
            nameAndVersion -> {
              logger.log(
                  FINE, "Auto-detected service.name from the jar file: {0}", nameAndVersion.name);

              return Resource.create(
                  Attributes.of(ResourceAttributes.SERVICE_NAME, nameAndVersion.name));
            })
        .orElse(Resource.empty());
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
