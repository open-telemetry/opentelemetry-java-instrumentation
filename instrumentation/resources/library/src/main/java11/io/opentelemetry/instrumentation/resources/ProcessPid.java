/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.lang.management.ManagementFactory;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

final class ProcessPid {

  private ProcessPid() {}

  @IgnoreJRERequirement
  static long getPid() {
    return ManagementFactory.getRuntimeMXBean().getPid();
  }
}
