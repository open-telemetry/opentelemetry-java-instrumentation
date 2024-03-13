/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.properties;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for propagators. */
@ConfigurationProperties(prefix = "otel")
public final class PropagationProperties {

  private List<String> propagators = Collections.emptyList();

  public List<String> getPropagators() {
    return propagators;
  }

  public void setPropagators(List<String> propagators) {
    this.propagators = propagators;
  }
}
