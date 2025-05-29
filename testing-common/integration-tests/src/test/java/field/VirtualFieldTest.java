/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VirtualFieldTest {

  @Test
  void testVirtualFields() {
    Assertions.assertTrue(virtualFieldTestMethod());
  }

  // instrumented by VirtualFieldTestInstrumentationModule
  private static boolean virtualFieldTestMethod() {
    return false;
  }
}
