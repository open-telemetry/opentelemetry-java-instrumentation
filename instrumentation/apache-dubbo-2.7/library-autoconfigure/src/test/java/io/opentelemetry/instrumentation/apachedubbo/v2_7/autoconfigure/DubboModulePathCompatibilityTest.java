/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import org.junit.jupiter.api.Test;

class DubboModulePathCompatibilityTest {

  @Test
  void autoconfigureAndLibraryPackagesAreDisjoint() {
    assertThat(OpenTelemetryClientFilter.class.getPackage().getName())
        .isNotEqualTo(DubboTelemetry.class.getPackage().getName());
    assertThat(OpenTelemetryServerFilter.class.getPackage().getName())
        .isNotEqualTo(DubboTelemetry.class.getPackage().getName());
  }
}
