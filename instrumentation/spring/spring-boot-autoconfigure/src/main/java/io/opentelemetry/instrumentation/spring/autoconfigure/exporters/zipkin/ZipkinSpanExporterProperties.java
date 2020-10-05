/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin;

import static io.opentelemetry.exporters.zipkin.ZipkinSpanExporter.DEFAULT_ENDPOINT;
import static io.opentelemetry.exporters.zipkin.ZipkinSpanExporter.DEFAULT_SERVICE_NAME;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link ZipkinSpanExporter}
 *
 * <p>Get Exporter Service Name {@link getServiceName()}
 *
 * <p>Get Exporter Endpoint
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.exporter.zipkin")
public class ZipkinSpanExporterProperties {

  private boolean enabled = true;
  private String serviceName = DEFAULT_SERVICE_NAME;
  private String endpoint = DEFAULT_ENDPOINT;

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
}
