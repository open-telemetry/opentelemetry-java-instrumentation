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
import java.io.InputStream;
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
    super(
        new AttributeProvider<Manifest>() {
          @Override
          public Optional<Manifest> readData() {
            try {
              Manifest manifest = new Manifest();
              InputStream systemResourceAsStream =
                  ClassLoader.getSystemResourceAsStream("META-INF/MANIFEST.MF");
              if (systemResourceAsStream == null) {
                return Optional.empty();
              }
              manifest.read(systemResourceAsStream);
              return Optional.of(manifest);
            } catch (IOException e) {
              logger.log(WARNING, "Error reading manifest", e);
              return Optional.empty();
            }
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
}
