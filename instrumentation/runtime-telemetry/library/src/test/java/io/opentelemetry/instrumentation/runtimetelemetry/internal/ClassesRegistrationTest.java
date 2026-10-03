/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassesRegistrationTest {
  @Test
  void reportsOnlySelectedObserversAfterRegistration() throws Exception {
    Set<String> registered = new HashSet<>();
    for (AutoCloseable observable :
        Classes.registerObservers(
            OpenTelemetry.noop().getMeter("test"), "jvm.class.count"::equals, registered::add)) {
      observable.close();
    }
    assertThat(registered).containsExactly("jvm.class.count");
  }

  @Test
  void failedRegistrationIsNotReported() {
    Meter meter = mock(Meter.class, RETURNS_DEEP_STUBS);
    when(meter
            .counterBuilder("jvm.class.loaded")
            .setDescription(any())
            .setUnit(any())
            .buildWithCallback(any()))
        .thenThrow(new IllegalStateException("registration failed"));
    Set<String> registered = new HashSet<>();
    assertThatThrownBy(() -> Classes.registerObservers(meter, unused -> true, registered::add))
        .isInstanceOf(IllegalStateException.class);
    assertThat(registered).isEmpty();
  }
}
