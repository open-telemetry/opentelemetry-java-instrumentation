/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.logging.LoggingSpanExporter} and {@link
 * io.opentelemetry.exporter.logging.LoggingMetricExporter}.
 */
@ConfigurationProperties(prefix = "otel.exporter.logging")
public final class LoggingExporterProperties {

  private boolean enabled = true;
  private final SignalProperties traces = new SignalProperties();
  private final SignalProperties metrics = new SignalProperties();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public SignalProperties getTraces() {
    return traces;
  }

  public SignalProperties getMetrics() {
    return metrics;
  }

  public static class SignalProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
