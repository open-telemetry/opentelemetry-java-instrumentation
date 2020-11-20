/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@link io.opentelemetry.exporter.logging.LoggingSpanExporter}. */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.logging")
public final class LoggingSpanExporterProperties {
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
