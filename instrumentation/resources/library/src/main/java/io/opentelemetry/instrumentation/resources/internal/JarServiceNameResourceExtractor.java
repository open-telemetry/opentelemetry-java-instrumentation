/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * A resource extractor that will attempt to detect the <code>service.name</code> from the
 * main jar file name.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JarServiceNameResourceExtractor {

  private static final Logger logger = Logger.getLogger(JarServiceNameResourceExtractor.class.getName());

  private final Supplier<Optional<Path>> jarPathSupplier;

  public JarServiceNameResourceExtractor() {
    this(MainJarPathHolder::getJarPath);
  }

  // visible for tests
  JarServiceNameResourceExtractor(MainJarPathFinder jarPathFinder) {
    this(() -> Optional.ofNullable(jarPathFinder.detectJarPath()));
  }

  private JarServiceNameResourceExtractor(Supplier<Optional<Path>> jarPathSupplier) {
    this.jarPathSupplier = jarPathSupplier;
  }

  public Resource extract() {
    return jarPathSupplier
        .get()
        .map(
            jarPath -> {
              String serviceName = getServiceName(jarPath);
              logger.log(
                  FINE, "Auto-detected service name from the jar file name: {0}", serviceName);
              return Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName));
            })
        .orElseGet(Resource::empty);
  }

  private static String getServiceName(Path jarPath) {
    String jarName = jarPath.getFileName().toString();
    int dotIndex = jarName.lastIndexOf(".");
    return dotIndex == -1 ? jarName : jarName.substring(0, dotIndex);
  }
}
