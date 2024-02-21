/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.IOException;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.logging.Logger;

/**
 * A {@link ResourceProvider} that will attempt to detect the <code>service.name</code> and <code>
 * service.version</code> from META-INF/MANIFEST.MF.
 */
@AutoService(ResourceProvider.class)
public final class ManifestResourceProvider extends AttributeResourceProvider<Manifest> {

  private static final Logger logger = Logger.getLogger(ManifestResourceProvider.class.getName());

  public ManifestResourceProvider() {
    this(new SystemHelper());
  }

  // Visible for testing
  ManifestResourceProvider(SystemHelper systemHelper) {
    super(
        new AttributeProvider<Manifest>() {
          @Override
          public Optional<Manifest> readData() {
            return Optional.ofNullable(
                    systemHelper.openClasspathResource("META-INF", "MANIFEST.MF"))
                .flatMap(
                    s -> {
                      try {
                        Manifest manifest = new Manifest();
                        manifest.read(s);
                        return Optional.of(manifest);
                      } catch (IOException e) {
                        logger.log(WARNING, "Error reading manifest", e);
                        return Optional.empty();
                      }
                    });
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

  @Override
  public int order() {
    // make it run later than SpringBootServiceNameDetector
    return 300;
  }
}
