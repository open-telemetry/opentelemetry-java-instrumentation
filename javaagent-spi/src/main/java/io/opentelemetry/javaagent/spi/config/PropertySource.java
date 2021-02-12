/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi.config;

import java.util.Map;

/**
 * A service provider that allows to override default OTel agent configuration. Properties returned
 * by implementations of this interface will be used after the following methods fail to find a
 * non-empty property value: system properties, environment variables, properties configuration
 * file.
 */
public interface PropertySource {
  /**
   * Returns all properties whose default values are overridden by this property source. Key of the
   * map is the propertyName (same as system property name, e.g. {@code otel.traces.exporter}),
   * value is the property value.
   */
  Map<String, String> getProperties();
}
