/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java Runtime Metrics Utility. */
public final class ProcessMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.oshi-5.0";

  private static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");

  // getResidentSetSize() was deprecated in oshi 6.11.0 and removed in 7.0.0; the replacement
  // getResidentMemory() was added in 6.11.0.
  @Nullable private static final Method RESIDENT_MEMORY_METHOD = findResidentMemoryMethod();

  @Nullable
  private static Method findResidentMemoryMethod() {
    for (String name : new String[] {"getResidentMemory", "getResidentSetSize"}) {
      try {
        return OSProcess.class.getMethod(name);
      } catch (NoSuchMethodException ignored) {
        // try next
      }
    }
    return null;
  }

  /** Register observers for java runtime metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return registerObservers(buildMeter(openTelemetry));
  }

  /**
   * Like {@link #registerObservers(OpenTelemetry)}, but accepts a pre-built {@link Meter}.
   *
   * @deprecated Exists only so the javaagent can emit the pre-rename {@code io.opentelemetry.oshi}
   *     scope by default; to be removed in 3.0 once v3-preview becomes the default.
   */
  @Deprecated
  public static List<AutoCloseable> registerObservers(Meter meter) {
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem osInfo = systemInfo.getOperatingSystem();
    OSProcess processInfo = osInfo.getProcess(osInfo.getProcessId());
    List<AutoCloseable> observables = new ArrayList<>();
    observables.add(
        meter
            .upDownCounterBuilder("runtime.java.memory")
            .setDescription("Runtime Java memory")
            .setUnit("By")
            .buildWithCallback(
                r -> {
                  processInfo.updateAttributes();
                  r.record(getResidentMemory(processInfo), Attributes.of(TYPE_KEY, "rss"));
                  r.record(processInfo.getVirtualSize(), Attributes.of(TYPE_KEY, "vms"));
                }));

    observables.add(
        meter
            .gaugeBuilder("runtime.java.cpu_time")
            .setDescription("Runtime Java CPU time")
            .setUnit("ms")
            .ofLongs()
            .buildWithCallback(
                r -> {
                  processInfo.updateAttributes();
                  r.record(processInfo.getUserTime(), Attributes.of(TYPE_KEY, "user"));
                  r.record(processInfo.getKernelTime(), Attributes.of(TYPE_KEY, "system"));
                }));
    return observables;
  }

  private static long getResidentMemory(OSProcess process) {
    if (RESIDENT_MEMORY_METHOD == null) {
      return 0;
    }
    try {
      return (long) RESIDENT_MEMORY_METHOD.invoke(process);
    } catch (ReflectiveOperationException ignored) {
      return 0;
    }
  }

  private static Meter buildMeter(OpenTelemetry openTelemetry) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  private ProcessMetrics() {}
}
