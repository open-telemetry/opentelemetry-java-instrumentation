/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/**
 * Registers OSHI system metrics with a supplied meter.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SystemMetricsInternal {

  // copied from SystemIncubatingAttributes
  private static final AttributeKey<String> SYSTEM_DEVICE = AttributeKey.stringKey("system.device");
  private static final AttributeKey<String> SYSTEM_MEMORY_STATE =
      AttributeKey.stringKey("system.memory.state");

  // copied from NetworkIncubatingAttributes
  private static final AttributeKey<String> NETWORK_INTERFACE_NAME =
      AttributeKey.stringKey("network.interface.name");
  private static final AttributeKey<String> NETWORK_IO_DIRECTION =
      AttributeKey.stringKey("network.io.direction");

  // copied from DiskIncubatingAttributes
  private static final AttributeKey<String> DISK_IO_DIRECTION =
      AttributeKey.stringKey("disk.io.direction");

  public static List<AutoCloseable> registerObservers(Meter meter) {
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();
    List<AutoCloseable> observables = new ArrayList<>();
    Attributes used = Attributes.of(SYSTEM_MEMORY_STATE, "used");
    Attributes free = Attributes.of(SYSTEM_MEMORY_STATE, "free");

    observables.add(
        meter
            .upDownCounterBuilder("system.memory.usage")
            .setDescription("Reports memory in use by state.")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  GlobalMemory mem = hal.getMemory();
                  r.record(mem.getTotal() - mem.getAvailable(), used);
                  r.record(mem.getAvailable(), free);
                }));

    observables.add(
        meter
            .gaugeBuilder("system.memory.utilization")
            .setDescription("Percentage of memory bytes in use.")
            .setUnit("1")
            .buildWithCallback(
                r -> {
                  GlobalMemory mem = hal.getMemory();
                  r.record(((double) (mem.getTotal() - mem.getAvailable())) / mem.getTotal(), used);
                  r.record(((double) mem.getAvailable()) / mem.getTotal(), free);
                }));

    observables.add(
        meter
            .counterBuilder("system.network.io")
            .setDescription("The number of bytes transmitted and received.")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getBytesRecv();
                    long sent = networkIf.getBytesSent();
                    String device = networkIf.getName();
                    r.record(
                        recv,
                        Attributes.of(
                            NETWORK_INTERFACE_NAME, device, NETWORK_IO_DIRECTION, "receive"));
                    r.record(
                        sent,
                        Attributes.of(
                            NETWORK_INTERFACE_NAME, device, NETWORK_IO_DIRECTION, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.network.packet.count")
            .setDescription("The number of packets transferred.")
            .setUnit("{packet}")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getPacketsRecv();
                    long sent = networkIf.getPacketsSent();
                    String device = networkIf.getName();
                    r.record(
                        recv,
                        Attributes.of(SYSTEM_DEVICE, device, NETWORK_IO_DIRECTION, "receive"));
                    r.record(
                        sent,
                        Attributes.of(SYSTEM_DEVICE, device, NETWORK_IO_DIRECTION, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.network.errors")
            .setDescription("Count of network errors detected.")
            .setUnit("{error}")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getInErrors();
                    long sent = networkIf.getOutErrors();
                    String device = networkIf.getName();
                    r.record(
                        recv,
                        Attributes.of(
                            NETWORK_INTERFACE_NAME, device, NETWORK_IO_DIRECTION, "receive"));
                    r.record(
                        sent,
                        Attributes.of(
                            NETWORK_INTERFACE_NAME, device, NETWORK_IO_DIRECTION, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.disk.io")
            .setDescription("Disk bytes transferred.")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  for (HWDiskStore diskStore : hal.getDiskStores()) {
                    diskStore.updateAttributes();
                    long read = diskStore.getReadBytes();
                    long write = diskStore.getWriteBytes();
                    String device = diskStore.getName();
                    r.record(read, Attributes.of(SYSTEM_DEVICE, device, DISK_IO_DIRECTION, "read"));
                    r.record(
                        write, Attributes.of(SYSTEM_DEVICE, device, DISK_IO_DIRECTION, "write"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.disk.operations")
            .setDescription("Disk operations count.")
            .setUnit("{operation}")
            .buildWithCallback(
                r -> {
                  for (HWDiskStore diskStore : hal.getDiskStores()) {
                    diskStore.updateAttributes();
                    long read = diskStore.getReads();
                    long write = diskStore.getWrites();
                    String device = diskStore.getName();
                    r.record(read, Attributes.of(SYSTEM_DEVICE, device, DISK_IO_DIRECTION, "read"));
                    r.record(
                        write, Attributes.of(SYSTEM_DEVICE, device, DISK_IO_DIRECTION, "write"));
                  }
                }));

    return observables;
  }

  private SystemMetricsInternal() {}
}
