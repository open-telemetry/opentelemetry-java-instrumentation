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
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/** System Metrics Utility. */
public class SystemMetrics {

  private SystemMetrics() {}

  /** Register observers for system metrics. */
  public static void registerObservers() {
    Meter meter = OpenTelemetry.getGlobalMeterProvider().get(SystemMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();

    meter
        .longUpDownSumObserverBuilder("system.memory.usage")
        .setDescription("System memory usage")
        .setUnit("By")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                GlobalMemory mem = hal.getMemory();
                r.observe(mem.getTotal() - mem.getAvailable(), Labels.of("state", "used"));
                r.observe(mem.getAvailable(), Labels.of("state", "free"));
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
                    Labels.of("state", "used"));
                r.observe(
                    ((double) mem.getAvailable()) / mem.getTotal(), Labels.of("state", "free"));
              }
            });

    meter
        .longSumObserverBuilder("system.network.io")
        .setDescription("System network IO")
        .setUnit("By")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  long recv = networkIf.getBytesRecv();
                  long sent = networkIf.getBytesSent();
                  String device = networkIf.getName();
                  r.observe(recv, Labels.of("device", device, "direction", "receive"));
                  r.observe(sent, Labels.of("device", device, "direction", "transmit"));
                }
              }
            });

    meter
        .longSumObserverBuilder("system.network.packets")
        .setDescription("System network packets")
        .setUnit("packets")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  long recv = networkIf.getPacketsRecv();
                  long sent = networkIf.getPacketsSent();
                  String device = networkIf.getName();
                  r.observe(recv, Labels.of("device", device, "direction", "receive"));
                  r.observe(sent, Labels.of("device", device, "direction", "transmit"));
                }
              }
            });

    meter
        .longSumObserverBuilder("system.network.errors")
        .setDescription("System network errors")
        .setUnit("errors")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {

                for (NetworkIF networkIf : hal.getNetworkIFs()) {
                  networkIf.updateAttributes();
                  long recv = networkIf.getInErrors();
                  long sent = networkIf.getOutErrors();
                  String device = networkIf.getName();
                  r.observe(recv, Labels.of("device", device, "direction", "receive"));
                  r.observe(sent, Labels.of("device", device, "direction", "transmit"));
                }
              }
            });

    meter
        .longSumObserverBuilder("system.disk.io")
        .setDescription("System disk IO")
        .setUnit("By")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {
                for (HWDiskStore diskStore : hal.getDiskStores()) {
                  long read = diskStore.getReadBytes();
                  long write = diskStore.getWriteBytes();
                  String device = diskStore.getName();
                  r.observe(read, Labels.of("device", device, "direction", "read"));
                  r.observe(write, Labels.of("device", device, "direction", "write"));
                }
              }
            });

    meter
        .longSumObserverBuilder("system.disk.operations")
        .setDescription("System disk operations")
        .setUnit("operations")
        .build()
        .setCallback(
            new Callback<LongResult>() {
              @Override
              public void update(LongResult r) {

                for (HWDiskStore diskStore : hal.getDiskStores()) {
                  long read = diskStore.getReads();
                  long write = diskStore.getWrites();
                  String device = diskStore.getName();
                  r.observe(read, Labels.of("device", device, "direction", "read"));
                  r.observe(write, Labels.of("device", device, "direction", "write"));
                }
              }
            });
  }
}
