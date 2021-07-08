/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for propagators. */
@ConfigurationProperties(prefix = "otel.propagation")
public final class PropagationProperties {

  private List<String> type = Arrays.asList("tracecontext", "baggage");

  public List<String> getType() {
    return type;
  }

  public void setType(List<String> type) {
    this.type = type;
  }
}
