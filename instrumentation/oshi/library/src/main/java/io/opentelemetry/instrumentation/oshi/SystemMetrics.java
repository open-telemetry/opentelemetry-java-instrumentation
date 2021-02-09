/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/** System Metrics Utility. */
public class SystemMetrics {
  private static final String DEVICE_LABEL_KEY = "device";
  private static final String DIRECTION_LABEL_KEY = "direction";
  private static final Labels LABEL_STATE_USED = Labels.of("state", "used");
  private static final Labels LABEL_STATE_FREE = Labels.of("state", "free");

  private SystemMetrics() {}

  /** Register observers for system metrics. */
  public static void registerObservers() {
    Meter meter = GlobalMetricsProvider.get().get("io.opentelemetry.instrumentation.oshi");
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();

    meter
        .longUpDownSumObserverBuilder("system.memory.usage")
        .setDescription("System memory usage")
        .setUnit("By")
        .setUpdater(
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.observe(mem.getTotal() - mem.getAvailable(), LABEL_STATE_USED);
              r.observe(mem.getAvailable(), LABEL_STATE_FREE);
            })
        .build();

    meter
        .doubleValueObserverBuilder("system.memory.utilization")
        .setDescription("System memory utilization")
        .setUnit("1")
        .setUpdater(
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.observe(
                  ((double) (mem.getTotal() - mem.getAvailable())) / mem.getTotal(),
                  LABEL_STATE_USED);
              r.observe(((double) mem.getAvailable()) / mem.getTotal(), LABEL_STATE_FREE);
            })
        .build();

    meter
        .longSumObserverBuilder("system.network.io")
        .setDescription("System network IO")
        .setUnit("By")
        .setUpdater(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getBytesRecv();
                long sent = networkIf.getBytesSent();
                String device = networkIf.getName();
                r.observe(
                    recv, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "receive"));
                r.observe(
                    sent, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "transmit"));
              }
            })
        .build();

    meter
        .longSumObserverBuilder("system.network.packets")
        .setDescription("System network packets")
        .setUnit("packets")
        .setUpdater(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getPacketsRecv();
                long sent = networkIf.getPacketsSent();
                String device = networkIf.getName();
                r.observe(
                    recv, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "receive"));
                r.observe(
                    sent, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "transmit"));
              }
            })
        .build();

    meter
        .longSumObserverBuilder("system.network.errors")
        .setDescription("System network errors")
        .setUnit("errors")
        .setUpdater(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getInErrors();
                long sent = networkIf.getOutErrors();
                String device = networkIf.getName();
                r.observe(
                    recv, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "receive"));
                r.observe(
                    sent, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "transmit"));
              }
            })
        .build();

    meter
        .longSumObserverBuilder("system.disk.io")
        .setDescription("System disk IO")
        .setUnit("By")
        .setUpdater(
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReadBytes();
                long write = diskStore.getWriteBytes();
                String device = diskStore.getName();
                r.observe(read, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "read"));
                r.observe(write, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "write"));
              }
            })
        .build();

    meter
        .longSumObserverBuilder("system.disk.operations")
        .setDescription("System disk operations")
        .setUnit("operations")
        .setUpdater(
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReads();
                long write = diskStore.getWrites();
                String device = diskStore.getName();
                r.observe(read, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "read"));
                r.observe(write, Labels.of(DEVICE_LABEL_KEY, device, DIRECTION_LABEL_KEY, "write"));
              }
            })
        .build();
  }
}
