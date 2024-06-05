/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
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
