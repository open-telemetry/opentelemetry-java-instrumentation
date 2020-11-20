/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import static io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter.DEFAULT_DEADLINE_MS;
import static io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter.DEFAULT_ENDPOINT;
import static io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter.DEFAULT_SERVICE_NAME;

import java.time.Duration;
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
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.jaeger")
public final class JaegerSpanExporterProperties {

  private boolean enabled = true;
  private String serviceName = DEFAULT_SERVICE_NAME;
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
