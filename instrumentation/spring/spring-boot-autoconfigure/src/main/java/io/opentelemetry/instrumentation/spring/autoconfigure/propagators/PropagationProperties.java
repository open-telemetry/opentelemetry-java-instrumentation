/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@link CompositeTextMapPropagator}. */
@ConfigurationProperties("otel.propagators")
public final class PropagationProperties {

  private List<PropagationType> type = Collections.singletonList(PropagationType.W3C);

  public List<PropagationType> getType() {
    return type;
  }

  public void setType(List<PropagationType> type) {
    this.type = type;
  }
}
