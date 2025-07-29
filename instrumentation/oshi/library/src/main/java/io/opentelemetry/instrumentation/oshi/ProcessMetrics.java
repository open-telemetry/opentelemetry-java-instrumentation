/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public class ProcessMetrics {
  private ProcessMetrics() {}

  /** Register observers for java runtime metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    Meter meter = openTelemetry.getMeterProvider().get("io.opentelemetry.oshi");
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());
    List<AutoCloseable> observables = new ArrayList<>();

    observables.add(
        Metrics.createRuntimeJavaMemory(
            meter,
            r -> {
              processInfo.updateAttributes();
              r.record(
                  processInfo.getResidentSetSize(), Attributes.of(CustomAttributes.TYPE, "rss"));
              r.record(processInfo.getVirtualSize(), Attributes.of(CustomAttributes.TYPE, "vms"));
            }));

    observables.add(
        Metrics.createRuntimeJavaCpuTime(
            meter,
            r -> {
              processInfo.updateAttributes();
              r.record(processInfo.getUserTime(), Attributes.of(CustomAttributes.TYPE, "user"));
              r.record(processInfo.getKernelTime(), Attributes.of(CustomAttributes.TYPE, "system"));
            }));

    return observables;
  }
}
