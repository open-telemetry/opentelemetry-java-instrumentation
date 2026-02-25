/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VirtualFieldTest {

  @Test
  void testVirtualFields() {
    assertThat(virtualFieldTestMethod()).isTrue();
  }

  // instrumented by VirtualFieldTestInstrumentationModule
  private static boolean virtualFieldTestMethod() {
    return false;
  }
}
