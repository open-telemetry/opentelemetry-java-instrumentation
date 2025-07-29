/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/** System Metrics Utility. */
public class SystemMetrics {
  private static final Attributes ATTRIBUTES_USED = Attributes.of(CustomAttributes.STATE, "used");
  private static final Attributes ATTRIBUTES_FREE = Attributes.of(CustomAttributes.STATE, "free");

  private SystemMetrics() {}

  /** Register observers for system metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    Meter meter = openTelemetry.getMeterProvider().get("io.opentelemetry.oshi");
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();
    List<AutoCloseable> observables = new ArrayList<>();

    observables.add(
        Metrics.createSystemMemoryUsage(
            meter,
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.record(mem.getTotal() - mem.getAvailable(), ATTRIBUTES_USED);
              r.record(mem.getAvailable(), ATTRIBUTES_FREE);
            }));

    observables.add(
        Metrics.createSystemMemoryUtilization(
            meter,
            r -> {
              GlobalMemory mem = hal.getMemory();
              r.record(
                  ((double) (mem.getTotal() - mem.getAvailable())) / mem.getTotal(),
                  ATTRIBUTES_USED);
              r.record(((double) mem.getAvailable()) / mem.getTotal(), ATTRIBUTES_FREE);
            }));

    observables.add(
        Metrics.createSystemNetworkIo(
            meter,
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getBytesRecv();
                long sent = networkIf.getBytesSent();
                String device = networkIf.getName();
                r.record(
                    recv,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "receive"));
                r.record(
                    sent,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "transmit"));
              }
            }));

    observables.add(
        Metrics.createSystemNetworkPackets(
            meter,
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getPacketsRecv();
                long sent = networkIf.getPacketsSent();
                String device = networkIf.getName();
                r.record(
                    recv,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "receive"));
                r.record(
                    sent,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "transmit"));
              }
            }));

    observables.add(
        Metrics.createSystemNetworkErrors(
            meter,
            r -> {
              for (NetworkIF networkIf : hal.getNetworkIFs()) {
                networkIf.updateAttributes();
                long recv = networkIf.getInErrors();
                long sent = networkIf.getOutErrors();
                String device = networkIf.getName();
                r.record(
                    recv,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "receive"));
                r.record(
                    sent,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "transmit"));
              }
            }));

    observables.add(
        Metrics.createSystemDiskIo(
            meter,
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReadBytes();
                long write = diskStore.getWriteBytes();
                String device = diskStore.getName();
                r.record(
                    read,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "read"));
                r.record(
                    write,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "write"));
              }
            }));

    observables.add(
        Metrics.createSystemDiskOperations(
            meter,
            r -> {
              for (HWDiskStore diskStore : hal.getDiskStores()) {
                long read = diskStore.getReads();
                long write = diskStore.getWrites();
                String device = diskStore.getName();
                r.record(
                    read,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "read"));
                r.record(
                    write,
                    Attributes.of(
                        CustomAttributes.DEVICE, device, CustomAttributes.DIRECTION, "write"));
              }
            }));

    return observables;
  }
}
