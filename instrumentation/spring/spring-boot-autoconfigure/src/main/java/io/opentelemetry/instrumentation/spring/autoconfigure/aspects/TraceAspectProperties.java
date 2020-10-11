/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for enabling tracing aspects. */
@ConfigurationProperties(prefix = "opentelemetry.trace.aspects")
public final class TraceAspectProperties {
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
