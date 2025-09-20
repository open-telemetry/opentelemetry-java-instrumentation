/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class XRayPropagationTest extends PropagationTest {
  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      builder().env("otel.propagators", "xray").build();

  @Override
  protected SmokeTestInstrumentationExtension testing() {
    return testing;
  }
}
