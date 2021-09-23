/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public class ProcessMetrics {
  private static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");

  private ProcessMetrics() {}

  /** Register observers for java runtime metrics. */
  public static void registerObservers() {
    Meter meter = GlobalMeterProvider.get().get(ProcessMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());

    meter
        .gaugeBuilder("runtime.java.memory")
        .ofLongs()
        .setDescription("Runtime Java memory")
        .setUnit("bytes")
        .buildWithCallback(
            r -> {
              processInfo.updateAttributes();
              r.observe(processInfo.getResidentSetSize(), Attributes.of(TYPE_KEY, "rss"));
              r.observe(processInfo.getVirtualSize(), Attributes.of(TYPE_KEY, "vms"));
            });

    meter
        .gaugeBuilder("runtime.java.cpu_time")
        .setDescription("Runtime Java CPU time")
        .setUnit("seconds")
        .buildWithCallback(
            r -> {
              processInfo.updateAttributes();
              r.observe(processInfo.getUserTime() * 1000, Attributes.of(TYPE_KEY, "user"));
              r.observe(processInfo.getKernelTime() * 1000, Attributes.of(TYPE_KEY, "system"));
            });
  }
}
