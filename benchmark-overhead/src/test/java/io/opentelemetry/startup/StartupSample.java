/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

record StartupSample(
    Variant variant,
    int round,
    int order,
    boolean discarded,
    String status,
    double springSeconds,
    double jvmSeconds,
    double httpSeconds) {

  StartupSample {
    if (!status.equals("ok") && !status.equals("failed")) {
      throw new IllegalArgumentException("Startup sample status must be ok or failed: " + status);
    }
    if (status.equals("failed")
        && (!Double.isNaN(springSeconds)
            || !Double.isNaN(jvmSeconds)
            || !Double.isNaN(httpSeconds))) {
      throw new IllegalArgumentException("Failed startup samples must have NaN durations");
    }
  }
}
