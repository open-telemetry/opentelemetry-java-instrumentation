/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LambdaAdviceBridge {
  private LambdaAdviceBridge() {}

  public static void referenceForMuzzleOnly() {
    throw new UnsupportedOperationException(
        LambdaImpl.class.getName() + " referencing for muzzle, should never be actually called");
  }
}
