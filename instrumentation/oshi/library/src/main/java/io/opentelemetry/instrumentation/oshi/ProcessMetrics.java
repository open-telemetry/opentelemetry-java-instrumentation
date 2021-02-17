/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public class ProcessMetrics {
  private static final String TYPE_LABEL_KEY = "type";

  private ProcessMetrics() {}

  /** Register observers for java runtime metrics. */
  public static void registerObservers() {
    Meter meter = GlobalMetricsProvider.get().get(ProcessMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());

    meter
        .longUpDownSumObserverBuilder("runtime.java.memory")
        .setDescription("Runtime Java memory")
        .setUnit("bytes")
        .setUpdater(
            r -> {
              processInfo.updateAttributes();
              r.observe(processInfo.getResidentSetSize(), Labels.of(TYPE_LABEL_KEY, "rss"));
              r.observe(processInfo.getVirtualSize(), Labels.of(TYPE_LABEL_KEY, "vms"));
            })
        .build();

    meter
        .doubleValueObserverBuilder("runtime.java.cpu_time")
        .setDescription("Runtime Java CPU time")
        .setUnit("seconds")
        .setUpdater(
            r -> {
              processInfo.updateAttributes();
              r.observe(processInfo.getUserTime() * 1000, Labels.of(TYPE_LABEL_KEY, "user"));
              r.observe(processInfo.getKernelTime() * 1000, Labels.of(TYPE_LABEL_KEY, "system"));
            })
        .build();
  }
}
