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
import io.opentelemetry.semconv.ServiceAttributes;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Note: The spring starter already includes provider in
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
    return getServiceVersionFromBuildInfo()
        .map(
            version -> {
              logger.log(FINE, "Auto-detected Spring Boot service version: {0}", version);
              return Resource.builder().put(ServiceAttributes.SERVICE_VERSION, version).build();
            })
        .orElseGet(Resource::empty);
  }

  private Optional<String> getServiceVersionFromBuildInfo() {
    try (InputStream in = system.openClasspathResource("META-INF", "build-info.properties")) {
      return in != null ? getServiceVersionPropertyFromStream(in) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static Optional<String> getServiceVersionPropertyFromStream(InputStream in) {
    Properties properties = new Properties();
    try {
      // Note: load() uses ISO 8859-1 encoding, same as spring uses by default for property files
      properties.load(in);
      return Optional.ofNullable(properties.getProperty("build.version"));
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
