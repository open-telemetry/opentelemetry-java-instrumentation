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

  private List<PropagationType> type =
      Arrays.asList(PropagationType.tracecontext, PropagationType.baggage);

  public List<PropagationType> getType() {
    return type;
  }

  public void setType(List<PropagationType> type) {
    this.type = type;
  }
}
