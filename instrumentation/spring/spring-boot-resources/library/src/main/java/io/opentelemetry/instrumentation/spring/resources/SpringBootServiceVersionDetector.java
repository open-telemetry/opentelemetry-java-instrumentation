/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Detects <code>service.name</code> and <code>service.version</code> from Spring Boot's <code>
 * build-info.properties</code> file.
 *
 * <p>Use the following snippet in your gradle file to generate the build-info.properties file:
 *
 * <pre>{@code
 * springBoot {
 *   buildInfo {
 *   }
 * }
 * }</pre>
 * <p>Note: The spring starter already includes provider in
 * io.opentelemetry.instrumentation.spring.autoconfigure.resources.SpringResourceProvider
 */
@AutoService(ResourceProvider.class)
public class SpringBootServiceVersionDetector implements ResourceProvider {

  private static final Logger logger =
      Logger.getLogger(SpringBootServiceVersionDetector.class.getName());

  private final SystemHelper system;

  public SpringBootServiceVersionDetector() {
    this.system = new SystemHelper();
  }

  // Exists for testing
  SpringBootServiceVersionDetector(SystemHelper system) {
    this.system = system;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return getPropertiesFromBuildInfo()
        .map(
            properties -> {
              logger.log(FINE, "Auto-detected Spring Boot service version: {0}", properties);
              ResourceBuilder builder = Resource.builder();

              String version = properties.getProperty("build.version");
              if (version != null) {
                builder.put(ResourceAttributes.SERVICE_VERSION, version);
              }

              String name = properties.getProperty("build.name");
              if (name != null) {
                builder.put(ResourceAttributes.SERVICE_NAME, name);
              }

              return builder.build();
            })
        .orElseGet(Resource::empty);
  }

  private Optional<Properties> getPropertiesFromBuildInfo() {
    try (InputStream in = system.openClasspathResource("META-INF", "build-info.properties")) {
      return in != null ? getPropertiesFromStream(in) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static Optional<Properties> getPropertiesFromStream(InputStream in) {
    Properties properties = new Properties();
    try {
      // Note: load() uses ISO 8859-1 encoding, same as spring uses by default for property files
      properties.load(in);
      return Optional.of(properties);
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
