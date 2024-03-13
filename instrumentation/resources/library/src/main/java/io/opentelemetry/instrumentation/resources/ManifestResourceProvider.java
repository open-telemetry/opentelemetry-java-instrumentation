/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
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
    this(new JarPathFinder(), ManifestResourceProvider::readManifest);
  }

  // Visible for testing
  ManifestResourceProvider(
      JarPathFinder jarPathFinder, Function<Path, Optional<Manifest>> manifestReader) {
    super(
        new AttributeProvider<Manifest>() {
          @Override
          public Optional<Manifest> readData() {
            return jarPathFinder.getJarPath().flatMap(manifestReader);
          }

          @Override
          public void registerAttributes(Builder<Manifest> builder) {
            builder
                .add(
                    ResourceAttributes.SERVICE_NAME,
                    manifest -> {
                      String serviceName =
                          manifest.getMainAttributes().getValue("Implementation-Title");
                      return Optional.ofNullable(serviceName);
                    })
                .add(
                    ResourceAttributes.SERVICE_VERSION,
                    manifest -> {
                      String serviceVersion =
                          manifest.getMainAttributes().getValue("Implementation-Version");
                      return Optional.ofNullable(serviceVersion);
                    });
          }
        });
  }

  private static Optional<Manifest> readManifest(Path jarPath) {
    try (InputStream s =
        new URL(String.format("jar:%s!/META-INF/MANIFEST.MF", jarPath.toUri())).openStream()) {
      Manifest manifest = new Manifest();
      manifest.read(s);
      return Optional.of(manifest);
    } catch (Exception e) {
      logger.log(WARNING, "Error reading manifest", e);
      return Optional.empty();
    }
  }

  @Override
  public int order() {
    // make it run later than ManifestResourceProvider and SpringBootServiceNameDetector
    return 300;
  }
}
