/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the tracing instrumentation of Spring WebMVC
 *
 * <p>Sets default value of opentelemetry.trace.web.enabled to true if the configuration does not
 * exist in application context
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.web")
public final class WebMvcProperties {
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
