/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

public final class SqsAdviceBridge {
  private SqsAdviceBridge() {}

  public static void init() {
    // called from advice
    SqsImpl.init(); // Reference the actual, package-private, implementation class for Muzzle
  }
}
