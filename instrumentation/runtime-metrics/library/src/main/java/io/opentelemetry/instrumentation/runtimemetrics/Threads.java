/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Registers measurements that generate metrics about JVM threads.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Threads.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.threads.count{daemon=true} 2
 *   process.runtime.jvm.threads.count{daemon=false} 5
 * </pre>
 */
public final class Threads {

  // Visible for testing
  static final Threads INSTANCE = new Threads();

  static final AttributeKey<Boolean> DAEMON = AttributeKey.booleanKey("daemon");
  static final AttributeKey<String> STATE = AttributeKey.stringKey("state");

  /** Register observers for java runtime class metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    INSTANCE.registerObservers(openTelemetry, ManagementFactory.getThreadMXBean());
  }

  // Visible for testing
  void registerObservers(OpenTelemetry openTelemetry, ThreadMXBean threadBean) {
    Meter meter = RuntimeMetricsUtil.getMeter(openTelemetry);

    meter
        .upDownCounterBuilder("process.runtime.jvm.threads.count")
        .setDescription("Number of executing threads")
        .setUnit("1")
        .buildWithCallback(
            isJava9OrNewer() ? java9AndNewerCallback(threadBean) : java8Callback(threadBean));
  }

  @Nullable private static final MethodHandle THREAD_INFO_IS_DAEMON;

  static {
    MethodHandle isDaemon;
    try {
      isDaemon =
          MethodHandles.publicLookup()
              .findVirtual(ThreadInfo.class, "isDaemon", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      isDaemon = null;
    }
    THREAD_INFO_IS_DAEMON = isDaemon;
  }

  private static boolean isJava9OrNewer() {
    return THREAD_INFO_IS_DAEMON != null;
  }

  private static Consumer<ObservableLongMeasurement> java8Callback(ThreadMXBean threadBean) {
    return measurement -> {
      measurement.record(
          threadBean.getDaemonThreadCount(), Attributes.builder().put(DAEMON, true).build());
      measurement.record(
          threadBean.getThreadCount() - threadBean.getDaemonThreadCount(),
          Attributes.builder().put(DAEMON, false).build());
    };
  }

  private static Consumer<ObservableLongMeasurement> java9AndNewerCallback(
      ThreadMXBean threadBean) {
    return measurement -> {
      Map<Attributes, Long> counts = new HashMap<>();
      long[] threadIds = threadBean.getAllThreadIds();
      for (ThreadInfo threadInfo : threadBean.getThreadInfo(threadIds)) {
        Attributes threadAttributes = threadAttributes(threadInfo);
        counts.compute(threadAttributes, (k, value) -> value == null ? 1 : value + 1);
      }
      counts.forEach((threadAttributes, count) -> measurement.record(count, threadAttributes));
    };
  }

  private static Attributes threadAttributes(ThreadInfo threadInfo) {
    boolean isDaemon;
    try {
      isDaemon = (boolean) requireNonNull(THREAD_INFO_IS_DAEMON).invoke(threadInfo);
    } catch (Throwable e) {
      throw new IllegalStateException("Unexpected error happened during ThreadInfo#isDaemon()", e);
    }
    return Attributes.of(DAEMON, isDaemon, STATE, threadInfo.getThreadState().name());
  }

  private Threads() {}
}
