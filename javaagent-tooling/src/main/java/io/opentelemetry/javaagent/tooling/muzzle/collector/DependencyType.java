/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

enum DependencyType {
  // order is meaningful here! EXTENDS is more important than USES
  USES,
  EXTENDS;

  DependencyType max(DependencyType other) {
    return this.compareTo(other) < 0 ? other : this;
  }
}
