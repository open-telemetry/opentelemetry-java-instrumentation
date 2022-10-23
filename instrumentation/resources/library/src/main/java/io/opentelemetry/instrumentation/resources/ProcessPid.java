/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.lang.management.ManagementFactory;

final class ProcessPid {

  private ProcessPid() {}

  static long getPid() {
    // While this is not strictly defined, almost all commonly used JVMs format this as
    // pid@hostname.
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    int atIndex = runtimeName.indexOf('@');
    if (atIndex >= 0) {
      String pidString = runtimeName.substring(0, atIndex);
      try {
        return Long.parseLong(pidString);
      } catch (NumberFormatException ignored) {
        // Ignore parse failure.
      }
    }
    return -1;
  }
}
