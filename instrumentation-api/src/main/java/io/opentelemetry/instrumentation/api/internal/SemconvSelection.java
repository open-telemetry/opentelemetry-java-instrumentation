/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

class SemconvSelection {
  private final boolean emitOld;
  private final boolean emitStable;

  static SemconvSelection of(boolean emitOld, boolean emitStable) {
    return new SemconvSelection(emitOld, emitStable);
  }

  private SemconvSelection(boolean emitOld, boolean emitStable) {
    this.emitOld = emitOld;
    this.emitStable = emitStable;
  }

  boolean emitOld() {
    return emitOld;
  }

  boolean emitStable() {
    return emitStable;
  }
}
