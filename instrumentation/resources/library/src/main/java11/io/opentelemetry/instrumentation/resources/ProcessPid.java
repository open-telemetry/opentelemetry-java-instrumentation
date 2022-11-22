/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.lang.management.ManagementFactory;

final class ProcessPid {

  private ProcessPid() {}

  static long getPid() {
    return ManagementFactory.getRuntimeMXBean().getPid();
  }
}
