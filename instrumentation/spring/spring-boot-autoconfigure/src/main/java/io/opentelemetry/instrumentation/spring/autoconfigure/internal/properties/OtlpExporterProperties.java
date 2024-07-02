/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConfigurationProperties(prefix = "otel.exporter.otlp")
public final class OtlpExporterProperties {

  private final Map<String, String> headers = new HashMap<>();

  private final SignalProperties traces = new SignalProperties();
  private final SignalProperties metrics = new SignalProperties();
  private final SignalProperties logs = new SignalProperties();

  public Map<String, String> getHeaders() {
    return headers;
  }

  public SignalProperties getTraces() {
    return traces;
  }

  public SignalProperties getMetrics() {
    return metrics;
  }

  public SignalProperties getLogs() {
    return logs;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class SignalProperties {
    private final Map<String, String> headers = new HashMap<>();

    public Map<String, String> getHeaders() {
      return headers;
    }
  }
}
