/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JmxRuntimeMetricsFactoryTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void registersOnlySelectedObservables() {
    List<AutoCloseable> observables =
        JmxRuntimeMetricsFactory.buildObservables(
            false, false, "jvm.class.count"::equals, OpenTelemetry.noop().getMeter("test"));
    observables.forEach(cleanup::deferCleanup);

    assertThat(observables).hasSize(1);
  }
}
