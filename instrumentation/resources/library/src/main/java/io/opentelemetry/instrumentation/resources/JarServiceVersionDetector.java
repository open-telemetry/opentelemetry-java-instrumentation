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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link ResourceProvider} that will attempt to detect the application version from the jar name.
 */
@AutoService(ResourceProvider.class)
public final class JarServiceVersionDetector extends JarResourceDetector {

  @SuppressWarnings("unused") // SPI
  public JarServiceVersionDetector() {
    this(
        ProcessArguments::getProcessArguments,
        System::getProperty,
        Files::isRegularFile,
        CACHE_LOOKUP);
  }

  // visible for tests
  JarServiceVersionDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists,
      Function<Supplier<Optional<String>>, Optional<String>> jarNameCacheLookup) {
    super(getProcessHandleArguments, getSystemProperty, fileExists, jarNameCacheLookup);
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return getServiceNameAndVersion()
        .flatMap(n -> n.version)
        .map(
            version -> {
              logger.log(FINE, "Auto-detected service.version from the jar file: {0}", version);

              return Resource.create(Attributes.of(ResourceAttributes.SERVICE_VERSION, version));
            })
        .orElse(Resource.empty());
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config
            .getMap("otel.resource.attributes")
            .containsKey(ResourceAttributes.SERVICE_VERSION.getKey())
        && existing.getAttribute(ResourceAttributes.SERVICE_VERSION) == null;
  }
}
