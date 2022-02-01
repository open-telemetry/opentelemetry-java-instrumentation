/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/** System Metrics Utility. */
public class SystemMetrics {
  private static final AttributeKey<String> DEVICE_KEY = AttributeKey.stringKey("device");
  private static final AttributeKey<String> DIRECTION_KEY = AttributeKey.stringKey("direction");

  private static final AttributeKey<String> STATE_KEY = AttributeKey.stringKey("state");

  private static final Attributes ATTRIBUTES_USED = Attributes.of(STATE_KEY, "used");
  private static final Attributes ATTRIBUTES_FREE = Attributes.of(STATE_KEY, "free");

  private SystemMetrics() {}

  /** Register observers for system metrics. */
  public static void registerObservers() {
    // TODO(anuraaga): registerObservers should accept an OpenTelemetry instance
    Meter meter = GlobalOpenTelemetry.get().getMeterProvider().get("io.opentelemetry.oshi");
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();

    meter
        .upDownCounterBuilder("system.memory.usage")
        .setDescription("System memory usage")
        .setUnit("By")
        .buildWithCallback(
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.record(mem.getTotal() - mem.getAvailable(), ATTRIBUTES_USED);
              r.record(mem.getAvailable(), ATTRIBUTES_FREE);
            });

    meter
        .gaugeBuilder("system.memory.utilization")
        .setDescription("System memory utilization")
        .setUnit("1")
        .buildWithCallback(
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.record(
                  ((double) (mem.getTotal() - mem.getAvailable())) / mem.getTotal(),
                  ATTRIBUTES_USED);
              r.record(((double) mem.getAvailable()) / mem.getTotal(), ATTRIBUTES_FREE);
            });

    meter
        .counterBuilder("system.network.io")
        .setDescription("System network IO")
        .setUnit("By")
        .buildWithCallback(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getBytesRecv();
                long sent = networkIf.getBytesSent();
                String device = networkIf.getName();
                r.record(recv, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "receive"));
                r.record(sent, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "transmit"));
              }
            });

    meter
        .counterBuilder("system.network.packets")
        .setDescription("System network packets")
        .setUnit("packets")
        .buildWithCallback(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getPacketsRecv();
                long sent = networkIf.getPacketsSent();
                String device = networkIf.getName();
                r.record(recv, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "receive"));
                r.record(sent, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "transmit"));
              }
            });

    meter
        .counterBuilder("system.network.errors")
        .setDescription("System network errors")
        .setUnit("errors")
        .buildWithCallback(
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getInErrors();
                long sent = networkIf.getOutErrors();
                String device = networkIf.getName();
                r.record(recv, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "receive"));
                r.record(sent, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "transmit"));
              }
            });

    meter
        .counterBuilder("system.disk.io")
        .setDescription("System disk IO")
        .setUnit("By")
        .buildWithCallback(
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReadBytes();
                long write = diskStore.getWriteBytes();
                String device = diskStore.getName();
                r.record(read, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "read"));
                r.record(write, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "write"));
              }
            });

    meter
        .counterBuilder("system.disk.operations")
        .setDescription("System disk operations")
        .setUnit("operations")
        .buildWithCallback(
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReads();
                long write = diskStore.getWrites();
                String device = diskStore.getName();
                r.record(read, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "read"));
                r.record(write, Attributes.of(DEVICE_KEY, device, DIRECTION_KEY, "write"));
              }
            });
  }
}
