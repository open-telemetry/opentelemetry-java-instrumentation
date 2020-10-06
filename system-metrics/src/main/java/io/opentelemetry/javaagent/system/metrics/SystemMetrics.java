/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.system.metrics;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument.Callback;
import io.opentelemetry.metrics.AsynchronousInstrument.DoubleResult;
import io.opentelemetry.metrics.AsynchronousInstrument.LongResult;
import io.opentelemetry.metrics.Meter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class SystemMetrics {
  private static final String TYPE_LABEL_KEY = "type";

  public static void registerObservers() {
    Meter meter = OpenTelemetry.getMeterProvider().get(SystemMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();

    HardwareAbstractionLayer hal = systemInfo.getHardware();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());

    meter
        .longValueObserverBuilder("system.memory.usage")
        .setDescription("System memory usage")
        .setUnit("bytes")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                GlobalMemory mem = hal.getMemory();
                r.observe(mem.getTotal() - mem.getAvailable(), Labels.of(TYPE_LABEL_KEY, "used"));
                r.observe(mem.getAvailable(), Labels.of(TYPE_LABEL_KEY, "free"));
              }
            });

    meter
        .doubleValueObserverBuilder("system.memory.utilization")
        .setDescription("System memory utilization")
        .setUnit("1")
        .build()
        .setCallback(
            new Callback<DoubleResult>() {
              @Override
              public void update(DoubleResult r) {
                GlobalMemory mem = hal.getMemory();
                r.observe(
                    ((double) (mem.getTotal() - mem.getAvailable())) / mem.getTotal(),
                    Labels.of(TYPE_LABEL_KEY, "used"));
                r.observe(
                    ((double) mem.getAvailable()) / mem.getTotal(),
                    Labels.of(TYPE_LABEL_KEY, "free"));
              }
            });

    meter
        .longValueObserverBuilder("system.network.io")
        .setDescription("System network IO")
        .setUnit("bytes")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long recv = 0;
                long sent = 0;

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  recv += networkIf.getBytesRecv();
                  sent += networkIf.getBytesSent();
                }

                r.observe(recv, Labels.of(TYPE_LABEL_KEY, "receive"));
                r.observe(sent, Labels.of(TYPE_LABEL_KEY, "transmit"));
              }
            });

    meter
        .longValueObserverBuilder("system.network.packets")
        .setDescription("System network packets")
        .setUnit("packets")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long recv = 0;
                long sent = 0;

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  recv += networkIf.getPacketsRecv();
                  sent += networkIf.getPacketsSent();
                }

                r.observe(recv, Labels.of(TYPE_LABEL_KEY, "receive"));
                r.observe(sent, Labels.of(TYPE_LABEL_KEY, "transmit"));
              }
            });

    meter
        .longValueObserverBuilder("system.network.errors")
        .setDescription("System network errors")
        .setUnit("errors")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long recv = 0;
                long sent = 0;

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  recv += networkIf.getInErrors();
                  sent += networkIf.getOutErrors();
                }

                r.observe(recv, Labels.of(TYPE_LABEL_KEY, "receive"));
                r.observe(sent, Labels.of(TYPE_LABEL_KEY, "transmit"));
              }
            });

    meter
        .longValueObserverBuilder("system.disk.io")
        .setDescription("System disk IO")
        .setUnit("bytes")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long read = 0;
                long write = 0;

                for (HWDiskStore diskStore : hal.getDiskStores()) {
                  read += diskStore.getReadBytes();
                  write += diskStore.getWriteBytes();
                }

                r.observe(read, Labels.of(TYPE_LABEL_KEY, "read"));
                r.observe(write, Labels.of(TYPE_LABEL_KEY, "write"));
              }
            });

    meter
        .longValueObserverBuilder("system.disk.operations")
        .setDescription("System disk operations")
        .setUnit("operations")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                long read = 0;
                long write = 0;

                for (HWDiskStore diskStore : hal.getDiskStores()) {
                  read += diskStore.getReads();
                  write += diskStore.getWrites();
                }

                r.observe(read, Labels.of(TYPE_LABEL_KEY, "read"));
                r.observe(write, Labels.of(TYPE_LABEL_KEY, "write"));
              }
            });

    meter
        .longValueObserverBuilder("runtime.java.memory")
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
