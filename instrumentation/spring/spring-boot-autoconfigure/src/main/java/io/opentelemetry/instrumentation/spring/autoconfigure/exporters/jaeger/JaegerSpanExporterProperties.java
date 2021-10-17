/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import java.time.Duration;
import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter}.
 *
 * <p>Get Exporter Service Name
 *
 * <p>Get Exporter Endpoint
 *
 * <p>Get max wait time for Collector to process Span Batches
 */
@ConfigurationProperties(prefix = "otel.exporter.jaeger")
public final class JaegerSpanExporterProperties {

  private boolean enabled = true;
  @Nullable private String endpoint;
  @Nullable private Duration timeout;

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
  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }
}
