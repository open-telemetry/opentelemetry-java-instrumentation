/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.assertj.core.api;

public final class AssertAccess {
  private AssertAccess() {}

  public static <ACTUAL> ACTUAL getActual(AbstractAssert<?, ACTUAL> abstractAssert) {
    return abstractAssert.actual;
  }
}
