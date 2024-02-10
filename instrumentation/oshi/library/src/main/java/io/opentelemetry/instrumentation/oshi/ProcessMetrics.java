/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public class ProcessMetrics {
  private static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");

  private ProcessMetrics() {}

  /** Register observers for java runtime metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    Meter meter = openTelemetry.getMeterProvider().get("io.opentelemetry.oshi");
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());
    List<AutoCloseable> observables = new ArrayList<>();
    observables.add(
        meter
            .upDownCounterBuilder("runtime.java.memory")
            .setDescription("Runtime Java memory")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  processInfo.updateAttributes();
                  r.record(processInfo.getResidentSetSize(), Attributes.of(TYPE_KEY, "rss"));
                  r.record(processInfo.getVirtualSize(), Attributes.of(TYPE_KEY, "vms"));
                }));

    observables.add(
        meter
            .gaugeBuilder("runtime.java.cpu_time")
            .setDescription("Runtime Java CPU time")
            .setUnit("ms")
            .ofLongs()
            .buildWithCallback(
                r -> {
                  processInfo.updateAttributes();
                  r.record(processInfo.getUserTime(), Attributes.of(TYPE_KEY, "user"));
                  r.record(processInfo.getKernelTime(), Attributes.of(TYPE_KEY, "system"));
                }));
    return observables;
  }
}
