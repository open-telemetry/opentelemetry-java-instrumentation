/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashSet;
import java.util.Set;
import javax.management.NotificationEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JmxRegistrationTest {
  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();
  private final Meter meter = OpenTelemetry.noop().getMeter("test");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jvm.class.count", "jvm.class.loaded", "jvm.class.unloaded",
        "jvm.memory.used", "jvm.memory.committed", "jvm.memory.limit",
        "jvm.memory.used_after_last_gc", "jvm.buffer.count", "jvm.buffer.memory.used",
        "jvm.buffer.memory.limit", "jvm.cpu.count", "jvm.thread.count"
      })
  void reportsOnlyTheSelectedReplacement(String name) {
    Set<String> registered = new HashSet<>();
    JmxRuntimeMetricsFactory.buildObservables(true, false, name::equals, meter, registered::add)
        .forEach(cleanup::deferCleanup);
    assertThat(registered).containsExactly(name);
  }

  @Test
  void experimentalMetricsAreNotReportedWhenDisabled() {
    Set<String> registered = new HashSet<>();
    JmxRuntimeMetricsFactory.buildObservables(
            false,
            false,
            name ->
                name.startsWith("jvm.buffer.")
                    || name.startsWith("jvm.system.")
                    || name.startsWith("jvm.file_descriptor."),
            meter,
            registered::add)
        .forEach(cleanup::deferCleanup);
    assertThat(registered).isEmpty();
  }

  @Test
  void unavailablePlatformMethodsAreNotReported() {
    Set<String> registered = new HashSet<>();
    Cpu.registerObservers(meter, null, null, unused -> true, registered::add)
        .forEach(cleanup::deferCleanup);
    FileDescriptor.registerObservers(
            meter, mock(OperatingSystemMXBean.class), unused -> true, registered::add)
        .forEach(cleanup::deferCleanup);
    SystemCpu.registerObservers(
            meter,
            mock(OperatingSystemMXBean.class),
            null,
            "jvm.system.cpu.utilization"::equals,
            registered::add)
        .forEach(cleanup::deferCleanup);
    assertThat(registered).isEmpty();
  }

  @Test
  void failedClassObserverIsNotReported() {
    Meter failing = mock(Meter.class, RETURNS_DEEP_STUBS);
    when(failing
            .counterBuilder("jvm.class.loaded")
            .setDescription(any())
            .setUnit(any())
            .buildWithCallback(any()))
        .thenThrow(new IllegalStateException("registration failed"));
    Set<String> registered = new HashSet<>();
    assertThatThrownBy(() -> Classes.registerObservers(failing, unused -> true, registered::add))
        .isInstanceOf(IllegalStateException.class);
    assertThat(registered).isEmpty();
  }

  @Test
  void failedMemoryObserverIsNotReported() {
    Meter failing = mock(Meter.class, RETURNS_DEEP_STUBS);
    when(failing
            .upDownCounterBuilder("jvm.memory.used")
            .setDescription(any())
            .setUnit(any())
            .buildWithCallback(any()))
        .thenThrow(new IllegalStateException("registration failed"));
    Set<String> registered = new HashSet<>();
    assertThatThrownBy(
            () -> MemoryPools.registerObservers(failing, unused -> true, registered::add))
        .isInstanceOf(IllegalStateException.class);
    assertThat(registered).isEmpty();
  }

  @Test
  void gcRequiresAnInstalledNotificationListener() {
    Set<String> registered = new HashSet<>();
    GarbageCollector.registerObservers(
            meter,
            singletonList(mock(GarbageCollectorMXBean.class)),
            notification -> {
              throw new AssertionError("no notification expected");
            },
            false,
            registered::add)
        .forEach(cleanup::deferCleanup);
    assertThat(registered).isEmpty();
    GarbageCollectorMXBean bean =
        mock(
            GarbageCollectorMXBean.class,
            withSettings().extraInterfaces(NotificationEmitter.class));
    GarbageCollector.registerObservers(
            meter,
            singletonList(bean),
            notification -> {
              throw new AssertionError("no notification expected");
            },
            false,
            registered::add)
        .forEach(cleanup::deferCleanup);
    assertThat(registered).containsExactly("jvm.gc.duration");
  }

  @Test
  void failedGcListenerIsNotReported() {
    GarbageCollectorMXBean bean =
        mock(
            GarbageCollectorMXBean.class,
            withSettings().extraInterfaces(NotificationEmitter.class));
    doThrow(new IllegalStateException("listener failed"))
        .when((NotificationEmitter) bean)
        .addNotificationListener(any(), any(), any());
    Set<String> registered = new HashSet<>();
    assertThatThrownBy(
            () ->
                GarbageCollector.registerObservers(
                    meter,
                    singletonList(bean),
                    notification -> {
                      throw new AssertionError("no notification expected");
                    },
                    false,
                    registered::add))
        .isInstanceOf(IllegalStateException.class);
    assertThat(registered).isEmpty();
  }
}
