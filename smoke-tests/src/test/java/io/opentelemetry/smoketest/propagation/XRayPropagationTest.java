/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import io.opentelemetry.smoketest.SmokeTestOptions;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class XRayPropagationTest extends PropagationTest {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    super.configure(options);
    options.env("otel.propagators", "xray");
  }
}
