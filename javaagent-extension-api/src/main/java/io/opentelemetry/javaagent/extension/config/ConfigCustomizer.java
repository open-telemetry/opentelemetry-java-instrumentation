/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.config;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.Ordered;
import java.util.Collections;
import java.util.Map;

/**
 * A service provider that allows to override default OTel javaagent configuration, and customize
 * the config just before it is set as the global.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface ConfigCustomizer extends Ordered {

  /**
   * Returns properties with their default values. Properties returned by implementations of this
   * interface will be used after the following methods fail to find a non-empty property value:
   * system properties, environment variables, properties configuration file.
   *
   * <p>Key of the map is the propertyName (same as system property name, e.g. {@code
   * otel.traces.exporter}), value is the property value.
   */
  default Map<String, String> defaultProperties() {
    return Collections.emptyMap();
  }

  /** Allows to change the javaagent configuration just before it is first used. */
  default Config customize(Config config) {
    return config;
  }
}
