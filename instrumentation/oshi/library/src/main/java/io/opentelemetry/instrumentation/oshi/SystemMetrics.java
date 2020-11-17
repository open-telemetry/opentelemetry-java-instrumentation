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
  private static final String TYPE_LABEL_KEY = "type";

  private SystemMetrics() {}

  /** Register observers for system metrics. */
  public static void registerObservers() {
    Meter meter = OpenTelemetry.getGlobalMeterProvider().get(SystemMetrics.class.getName());
    SystemInfo systemInfo = new SystemInfo();
    HardwareAbstractionLayer hal = systemInfo.getHardware();

    meter
        .longUpDownSumObserverBuilder("system.memory.usage")
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
        .longSumObserverBuilder("system.network.io")
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
        .longSumObserverBuilder("system.network.packets")
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
        .longSumObserverBuilder("system.network.errors")
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
        .longSumObserverBuilder("system.disk.io")
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
        .longSumObserverBuilder("system.disk.operations")
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
  }
}
