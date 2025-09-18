/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import io.opentelemetry.smoketest.SmokeTestTarget;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class B3MultiPropagationTest extends PropagationTest {

  @Override
  protected SmokeTestTarget.Builder customize(SmokeTestTarget.Builder builder) {
    return builder.env("otel.propagators", "b3multi");
  }
}
