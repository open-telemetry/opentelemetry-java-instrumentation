/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the tracing instrumentation of HTTP clients.
 *
 * <p>Sets default value of otel.springboot.httpclients.enabled to true if the configuration does
 * not exist in application context.
 */
@ConfigurationProperties(prefix = "otel.springboot.httpclients")
public final class HttpClientsProperties {
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
