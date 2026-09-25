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
 * Internal registration bridge for the javaagent's pre-rename meter scope.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SystemMetricsInternal {

  private static final AttributeKey<String> DEVICE_KEY = AttributeKey.stringKey("device");
  private static final AttributeKey<String> DIRECTION_KEY = AttributeKey.stringKey("direction");

  // copied from OtherIncubatingAttributes
  private static final AttributeKey<String> STATE_KEY = AttributeKey.stringKey("state");

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

  public static List<AutoCloseable> registerObservers(Meter meter, boolean v3Preview) {
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();
    List<AutoCloseable> observables = new ArrayList<>();
    AttributeKey<String> memoryState = v3Preview ? SYSTEM_MEMORY_STATE : STATE_KEY;
    AttributeKey<String> networkDevice = v3Preview ? NETWORK_INTERFACE_NAME : DEVICE_KEY;
    AttributeKey<String> networkDirection = v3Preview ? NETWORK_IO_DIRECTION : DIRECTION_KEY;
    AttributeKey<String> packetDevice = v3Preview ? SYSTEM_DEVICE : DEVICE_KEY;
    AttributeKey<String> diskDevice = v3Preview ? SYSTEM_DEVICE : DEVICE_KEY;
    AttributeKey<String> diskDirection = v3Preview ? DISK_IO_DIRECTION : DIRECTION_KEY;
    Attributes used = Attributes.of(memoryState, "used");
    Attributes free = Attributes.of(memoryState, "free");

    observables.add(
        meter
            .upDownCounterBuilder("system.memory.usage")
            .setDescription(v3Preview ? "Reports memory in use by state." : "System memory usage")
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
            .setDescription(
                v3Preview ? "Percentage of memory bytes in use." : "System memory utilization")
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
            .setDescription(
                v3Preview ? "The number of bytes transmitted and received." : "System network IO")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getBytesRecv();
                    long sent = networkIf.getBytesSent();
                    String device = networkIf.getName();
                    r.record(
                        recv, Attributes.of(networkDevice, device, networkDirection, "receive"));
                    r.record(
                        sent, Attributes.of(networkDevice, device, networkDirection, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder(v3Preview ? "system.network.packet.count" : "system.network.packets")
            .setDescription(
                v3Preview ? "The number of packets transferred." : "System network packets")
            .setUnit(v3Preview ? "{packet}" : "{packets}")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getPacketsRecv();
                    long sent = networkIf.getPacketsSent();
                    String device = networkIf.getName();
                    r.record(
                        recv, Attributes.of(packetDevice, device, networkDirection, "receive"));
                    r.record(
                        sent, Attributes.of(packetDevice, device, networkDirection, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.network.errors")
            .setDescription(
                v3Preview ? "Count of network errors detected." : "System network errors")
            .setUnit(v3Preview ? "{error}" : "{errors}")
            .buildWithCallback(
                r -> {
                  for (NetworkIF networkIf : hal.getNetworkIFs()) {
                    networkIf.updateAttributes();
                    long recv = networkIf.getInErrors();
                    long sent = networkIf.getOutErrors();
                    String device = networkIf.getName();
                    r.record(
                        recv, Attributes.of(networkDevice, device, networkDirection, "receive"));
                    r.record(
                        sent, Attributes.of(networkDevice, device, networkDirection, "transmit"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.disk.io")
            .setDescription(v3Preview ? "Disk bytes transferred." : "System disk IO")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  for (HWDiskStore diskStore : hal.getDiskStores()) {
                    diskStore.updateAttributes();
                    long read = diskStore.getReadBytes();
                    long write = diskStore.getWriteBytes();
                    String device = diskStore.getName();
                    r.record(read, Attributes.of(diskDevice, device, diskDirection, "read"));
                    r.record(write, Attributes.of(diskDevice, device, diskDirection, "write"));
                  }
                }));

    observables.add(
        meter
            .counterBuilder("system.disk.operations")
            .setDescription(v3Preview ? "Disk operations count." : "System disk operations")
            .setUnit(v3Preview ? "{operation}" : "{operations}")
            .buildWithCallback(
                r -> {
                  for (HWDiskStore diskStore : hal.getDiskStores()) {
                    diskStore.updateAttributes();
                    long read = diskStore.getReads();
                    long write = diskStore.getWrites();
                    String device = diskStore.getName();
                    r.record(read, Attributes.of(diskDevice, device, diskDirection, "read"));
                    r.record(write, Attributes.of(diskDevice, device, diskDirection, "write"));
                  }
                }));

    return observables;
  }

  private SystemMetricsInternal() {}
}
