/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.semconv.ResourceAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
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
    this(ProcessArguments::getProcessArguments, System::getProperty, Files::isRegularFile);
  }

  // visible for tests
  JarServiceVersionDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists) {
    super(getProcessHandleArguments, getSystemProperty, fileExists);
  }

  @Override
  protected void registerAttributes(
      PriorityResourceProvider<NameAndVersion>.AttributeBuilder attributeBuilder) {

    attributeBuilder.add(
        ResourceAttributes.SERVICE_VERSION,
        d -> {
          logger.log(FINE, "Auto-detected service.version from the jar file: {0}", d.version);
          return d.version;
        });
  }
}
