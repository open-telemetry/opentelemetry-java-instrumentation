/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class DefaultPropagationTest extends PropagationTest {
  @RegisterExtension
  static final SmokeTestInstrumentationExtension<Integer> testing = builder().build();

  @Override
  protected SmokeTestInstrumentationExtension<Integer> testing() {
    return testing;
  }
}
