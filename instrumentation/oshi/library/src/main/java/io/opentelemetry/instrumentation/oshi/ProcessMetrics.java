/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.AsynchronousInstrument.Callback;
import io.opentelemetry.api.metrics.AsynchronousInstrument.DoubleResult;
import io.opentelemetry.api.metrics.AsynchronousInstrument.LongResult;
import io.opentelemetry.api.metrics.Meter;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public class ProcessMetrics {
  private static final String TYPE_LABEL_KEY = "type";

  private ProcessMetrics() {}

  /** Register observers for java runtime metrics. */
  public static void registerObservers() {
    Meter meter = OpenTelemetry.getGlobalMeterProvider().get(ProcessMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());

    meter
        .longUpDownSumObserverBuilder("runtime.java.memory")
        .setDescription("Runtime Java memory")
        .setUnit("bytes")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                processInfo.updateAttributes();
                r.observe(processInfo.getResidentSetSize(), Labels.of(TYPE_LABEL_KEY, "rss"));
                r.observe(processInfo.getVirtualSize(), Labels.of(TYPE_LABEL_KEY, "vms"));
              }
            });

    meter
        .doubleValueObserverBuilder("runtime.java.cpu_time")
        .setDescription("Runtime Java CPU time")
        .setUnit("seconds")
        .build()
        .setCallback(
            new Callback<DoubleResult>() {
              @Override
              public void update(DoubleResult r) {
                processInfo.updateAttributes();
                r.observe(processInfo.getUserTime() * 1000, Labels.of(TYPE_LABEL_KEY, "user"));
                r.observe(processInfo.getKernelTime() * 1000, Labels.of(TYPE_LABEL_KEY, "system"));
              }
            });
  }
}
