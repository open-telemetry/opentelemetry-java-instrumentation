/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static io.opentelemetry.exporter.otlp.OtlpGrpcSpanExporter.DEFAULT_DEADLINE_MS;
import static io.opentelemetry.exporter.otlp.OtlpGrpcSpanExporter.DEFAULT_ENDPOINT;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.otlp.OtlpGrpcSpanExporter}.
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
  private String serviceName = "unknown";
  private String endpoint = DEFAULT_ENDPOINT;
  private Duration spanTimeout = Duration.ofMillis(DEFAULT_DEADLINE_MS);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public Duration getSpanTimeout() {
    return spanTimeout;
  }

  public void setSpanTimeout(Duration spanTimeout) {
    this.spanTimeout = spanTimeout;
  }
}
