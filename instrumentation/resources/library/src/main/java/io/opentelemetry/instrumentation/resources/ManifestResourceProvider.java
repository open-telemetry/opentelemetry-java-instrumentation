/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.resources.internal.ManifestResourceExtractor;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link ResourceProvider} that will attempt to detect the <code>service.name</code> and <code>
 * service.version</code> from META-INF/MANIFEST.MF.
 */
@AutoService(ResourceProvider.class)
public final class ManifestResourceProvider extends AttributeResourceProvider<Resource> {

  @SuppressWarnings("unused") // SPI
  public ManifestResourceProvider() {
    this(() -> new ManifestResourceExtractor().extract());
  }

  // Visible for testing
  ManifestResourceProvider(Supplier<Resource> resourceSupplier) {
    super(
        new AttributeProvider<Resource>() {
          @Override
          public Optional<Resource> readData() {
            return Optional.of(resourceSupplier.get());
          }

          @Override
          public void registerAttributes(Builder<Resource> builder) {
            builder
                .add(
                    ServiceAttributes.SERVICE_NAME,
                    r -> Optional.ofNullable(r.getAttribute(ServiceAttributes.SERVICE_NAME)))
                .add(
                    ServiceAttributes.SERVICE_VERSION,
                    r -> Optional.ofNullable(r.getAttribute(ServiceAttributes.SERVICE_VERSION)));
          }
        });
  }

  @Override
  public int order() {
    // make it run later than SpringBootServiceNameDetector
    return 300;
  }
}
