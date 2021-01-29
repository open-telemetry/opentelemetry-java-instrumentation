/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.zipkin.ZipkinSpanExporter}.
 *
 * <p>Get Exporter Service Name {@link #getServiceName()}
 *
 * <p>Get Exporter Endpoint
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.zipkin")
public class ZipkinSpanExporterProperties {

  private boolean enabled = true;
  @Nullable private String endpoint;

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
}
