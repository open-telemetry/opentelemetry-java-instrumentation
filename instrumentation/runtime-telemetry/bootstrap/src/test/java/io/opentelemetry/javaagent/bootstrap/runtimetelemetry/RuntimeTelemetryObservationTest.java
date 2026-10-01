/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.runtimetelemetry;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeTelemetryObservationTest {

  @Test
  void publishesImmutableCopy() {
    Set<String> before = RuntimeTelemetryObservation.registeredJmxObservers();
    assertThat(before).isEmpty();
    Set<String> input = new HashSet<>(singleton("jvm.class.count"));
    try {
      RuntimeTelemetryObservation.internalSetRegisteredJmxObservers(input);
      input.clear();

      assertThat(RuntimeTelemetryObservation.registeredJmxObservers())
          .containsExactly("jvm.class.count");
      assertThat(before).isEmpty();
      assertThatThrownBy(() -> RuntimeTelemetryObservation.registeredJmxObservers().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    } finally {
      RuntimeTelemetryObservation.internalSetRegisteredJmxObservers(emptySet());
    }
  }
}
