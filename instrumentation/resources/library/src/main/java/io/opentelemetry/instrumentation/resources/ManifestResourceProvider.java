/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
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
 * A {@link ResourceProvider} that will attempt to detect the <code>service.name</code> and <code>
 * service.version</code> from META-INF/MANIFEST.MF.
 */
@AutoService(ResourceProvider.class)
public final class ManifestResourceProvider extends AttributeResourceProvider<Manifest> {

  private static final Logger logger = Logger.getLogger(ManifestResourceProvider.class.getName());

  @SuppressWarnings("unused") // SPI
  public ManifestResourceProvider() {
    this(MainJarPathHolder::getJarPath, ManifestResourceProvider::readManifest);
  }

  private ManifestResourceProvider(
      Supplier<Optional<Path>> jarPathSupplier, Function<Path, Optional<Manifest>> manifestReader) {
    super(
        new AttributeProvider<Manifest>() {
          @Override
          public Optional<Manifest> readData() {
            return jarPathSupplier.get().flatMap(manifestReader);
          }

          @Override
          public void registerAttributes(Builder<Manifest> builder) {
            builder
                .add(
                    ServiceAttributes.SERVICE_NAME,
                    manifest -> {
                      String serviceName =
                          manifest.getMainAttributes().getValue("Implementation-Title");
                      return Optional.ofNullable(serviceName);
                    })
                .add(
                    ServiceAttributes.SERVICE_VERSION,
                    manifest -> {
                      String serviceVersion =
                          manifest.getMainAttributes().getValue("Implementation-Version");
                      return Optional.ofNullable(serviceVersion);
                    });
          }
        });
  }

  // Visible for testing
  ManifestResourceProvider(
      MainJarPathFinder jarPathFinder, Function<Path, Optional<Manifest>> manifestReader) {
    this(() -> Optional.ofNullable(jarPathFinder.detectJarPath()), manifestReader);
  }

  private static Optional<Manifest> readManifest(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile(), false)) {
      return Optional.of(jarFile.getManifest());
    } catch (IOException exception) {
      logger.log(WARNING, "Error reading manifest", exception);
      return Optional.empty();
    }
  }

  @Override
  public int order() {
    // make it run later than SpringBootServiceNameDetector
    return 300;
  }
}
