/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;

/**
 * Micrometer base units are passed through verbatim, unless the v3 preview is enabled, in which
 * case known units are normalized to UCUM.
 */
final class UnitAssertions {

  /** Returns {@code v3PreviewUnit} when the v3 preview is enabled, {@code unit} otherwise. */
  static String expectedUnit(String unit, String v3PreviewUnit) {
    return SemconvStability.v3Preview() ? v3PreviewUnit : unit;
  }

  private UnitAssertions() {}
}
