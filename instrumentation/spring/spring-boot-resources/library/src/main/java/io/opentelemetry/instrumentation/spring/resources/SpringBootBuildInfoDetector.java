/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class SpringBootBuildInfoDetector implements ConditionalResourceProvider {
  protected static final Logger logger =
      Logger.getLogger(SpringBootServiceVersionDetector.class.getName());

  private final AttributeKey<String> attributeKey;
  private final String propertyKey;
  private final SystemHelper system;

  protected SpringBootBuildInfoDetector(
      AttributeKey<String> attributeKey, String propertyKey, SystemHelper system) {
    this.attributeKey = attributeKey;
    this.propertyKey = propertyKey;
    this.system = system;
  }

  @Override
  public int order() {
    // make it run later SpringBootServiceNameDetector, which has a higher priority
    return 200;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return getPropertiesFromBuildInfo()
        .map(
            properties -> {
              ResourceBuilder builder = Resource.builder();

              String value = properties.getProperty(propertyKey);
              if (value != null) {
                logger.log(
                    FINE, "Auto-detected Spring Boot {0}: {1}", new Object[] {attributeKey, value});
                builder.put(attributeKey, value);
              }

              return builder.build();
            })
        .orElseGet(Resource::empty);
  }

  protected Optional<Properties> getPropertiesFromBuildInfo() {
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
