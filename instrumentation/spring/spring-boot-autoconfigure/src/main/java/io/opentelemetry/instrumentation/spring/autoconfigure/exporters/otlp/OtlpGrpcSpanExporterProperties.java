/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import java.time.Duration;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter}.
 *
 * <p>Get Exporter Service Name
 *
 * <p>Get Exporter Endpoint
 *
 * <p>Get max wait time for Collector to process Span Batches
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.otlp")
public final class OtlpGrpcSpanExporterProperties {

  private boolean enabled = true;
  @Nullable private String endpoint;
  @Nullable private Duration spanTimeout;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Nullable
  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  @Nullable
  public Duration getSpanTimeout() {
    return spanTimeout;
  }

  public void setSpanTimeout(Duration spanTimeout) {
    this.spanTimeout = spanTimeout;
  }
}
