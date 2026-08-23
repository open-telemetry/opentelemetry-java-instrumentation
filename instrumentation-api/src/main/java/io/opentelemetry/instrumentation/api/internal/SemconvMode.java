/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Objects;

final class SemconvMode {
  static final SemconvMode V0_STABLE = new SemconvMode(0, false, false);
  static final SemconvMode V1_STABLE = new SemconvMode(1, false, false);
  static final SemconvMode V1_EXPERIMENTAL = new SemconvMode(1, true, false);

  private final int version;
  private final boolean experimental;
  private final boolean dualEmit;

  private SemconvMode(int version, boolean experimental, boolean dualEmit) {
    this.version = version;
    this.experimental = experimental;
    this.dualEmit = dualEmit;
  }

  int version() {
    return version;
  }

  boolean experimental() {
    return experimental;
  }

  boolean dualEmit() {
    return dualEmit;
  }

  SemconvMode withDualEmit() {
    return new SemconvMode(version, experimental, true);
  }

  SemconvMode withoutDualEmit() {
    return new SemconvMode(version, experimental, false);
  }

  SemconvMode stable() {
    return new SemconvMode(version, false, false);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SemconvMode)) {
      return false;
    }
    SemconvMode other = (SemconvMode) obj;
    return version == other.version
        && experimental == other.experimental
        && dualEmit == other.dualEmit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, experimental, dualEmit);
  }
}
