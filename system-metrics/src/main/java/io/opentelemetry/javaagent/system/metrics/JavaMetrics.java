/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.system.metrics;

import static io.opentelemetry.javaagent.system.metrics.SystemMetrics.TYPE_LABEL_KEY;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Callback;
import io.opentelemetry.metrics.AsynchronousInstrument.DoubleResult;
import io.opentelemetry.metrics.AsynchronousInstrument.LongResult;
import io.opentelemetry.metrics.Meter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility */
public class JavaMetrics {

  private JavaMetrics() {}

  /** Register observers for java runtime metrics */
  public static void registerObservers() {
    Meter meter = OpenTelemetry.getMeterProvider().get(JavaMetrics.class.getName());
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

    meter
        .longValueObserverBuilder("runtime.java.gc_count")
        .setDescription("Runtime Java GC count")
        .setUnit("counts")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long gcCount = 0;
                for (final GarbageCollectorMXBean gcBean :
                    ManagementFactory.getGarbageCollectorMXBeans()) {
                  gcCount += gcBean.getCollectionCount();
                }

                r.observe(gcCount, Labels.of(TYPE_LABEL_KEY, "count"));
              }
            });
  }
}
