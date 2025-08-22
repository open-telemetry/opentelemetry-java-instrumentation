/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;

/**
 * A resource extractor that will attempt to detect the <code>service.name</code> and <code>
 * service.version</code> from META-INF/MANIFEST.MF.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ManifestResourceExtractor {

  private static final Logger logger = Logger.getLogger(ManifestResourceExtractor.class.getName());

  private final Supplier<Optional<Path>> jarPathSupplier;

  private final Function<Path, Optional<Manifest>> manifestReader;

  public ManifestResourceExtractor() {
    this(MainJarPathHolder::getJarPath, ManifestResourceExtractor::readManifest);
  }

  // Visible for testing
  ManifestResourceExtractor(
      MainJarPathFinder jarPathFinder, Function<Path, Optional<Manifest>> manifestReader) {
    this(() -> Optional.ofNullable(jarPathFinder.detectJarPath()), manifestReader);
  }

  private ManifestResourceExtractor(
      Supplier<Optional<Path>> jarPathSupplier, Function<Path, Optional<Manifest>> manifestReader) {
    this.jarPathSupplier = jarPathSupplier;
    this.manifestReader = manifestReader;
  }

  public Resource extract() {
    return jarPathSupplier
        .get()
        .flatMap(manifestReader)
        .map(manifest -> extract(manifest))
        .orElseGet(Resource::empty);
  }

  private static Resource extract(Manifest manifest) {
    String serviceName = manifest.getMainAttributes().getValue("Implementation-Title");
    AttributesBuilder builder = Attributes.builder();
    if (serviceName != null) {
      builder.put(ServiceAttributes.SERVICE_NAME, serviceName);
    }

    String serviceVersion = manifest.getMainAttributes().getValue("Implementation-Version");
    if (serviceVersion != null) {
      builder.put(ServiceAttributes.SERVICE_VERSION, serviceVersion);
    }
    return Resource.create(builder.build());
  }

  private static Optional<Manifest> readManifest(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile(), false)) {
      return Optional.of(jarFile.getManifest());
    } catch (IOException exception) {
      logger.log(WARNING, "Error reading manifest", exception);
      return Optional.empty();
    }
  }
}
