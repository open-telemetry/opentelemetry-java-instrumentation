/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin;

import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.zipkin.ZipkinSpanExporter}.
 *
 * <p>Get Exporter Endpoint
 */
@ConfigurationProperties(prefix = "otel.exporter.zipkin")
public class ZipkinSpanExporterProperties {

  @Nullable private String endpoint;

  @Nullable
  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }
}
