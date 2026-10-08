/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JvmMetricsOwnershipTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({
    "jvm.memory.used, GAUGE, jvm.memory.used",
    "jvm.memory.committed, GAUGE, jvm.memory.committed",
    "jvm.memory.max, GAUGE, jvm.memory.limit",
    "jvm.memory.limit, GAUGE, jvm.memory.limit",
    "jvm.buffer.count, GAUGE, jvm.buffer.count",
    "jvm.buffer.memory.used, GAUGE, jvm.buffer.memory.used",
    "jvm.buffer.total.capacity, GAUGE, jvm.buffer.memory.limit",
    "jvm.threads.live, GAUGE, jvm.thread.count",
    "jvm.threads.daemon, GAUGE, jvm.thread.count",
    "jvm.threads.states, GAUGE, jvm.thread.count",
    "jvm.thread.count, GAUGE, jvm.thread.count",
    "system.cpu.count, GAUGE, jvm.cpu.count",
    "jvm.cpu.count, GAUGE, jvm.cpu.count",
    "process.cpu.usage, GAUGE, jvm.cpu.recent_utilization",
    "jvm.cpu.recent_utilization, GAUGE, jvm.cpu.recent_utilization",
    "process.cpu.time, COUNTER, jvm.cpu.time",
    "jvm.cpu.time, COUNTER, jvm.cpu.time",
    "system.cpu.usage, GAUGE, jvm.system.cpu.utilization",
    "system.load.average.1m, GAUGE, jvm.system.cpu.load_1m",
    "process.files.open, GAUGE, jvm.file_descriptor.count",
    "process.files.max, GAUGE, jvm.file_descriptor.limit",
    "jvm.gc.pause, TIMER, jvm.gc.duration",
    "jvm.gc.concurrent.phase.time, TIMER, jvm.gc.duration",
    "jvm.classes.loaded, GAUGE, jvm.class.count",
    "jvm.class.count, GAUGE, jvm.class.count",
    "jvm.classes.loaded.count, COUNTER, jvm.class.loaded",
    "jvm.class.loaded, COUNTER, jvm.class.loaded",
    "jvm.classes.unloaded, COUNTER, jvm.class.unloaded",
    "jvm.class.unloaded, COUNTER, jvm.class.unloaded"
  })
  void suppressesOnlyWhenEnabledAndReplacementRegistered(
      String name, Meter.Type type, String observer) {
    Meter.Id id = id(name, type);
    assertThat(policy(false, emptySet(), singleton(observer)).test(id)).isFalse();
    assertThat(policy(true, emptySet(), emptySet()).test(id)).isFalse();
    assertThat(policy(true, emptySet(), singleton("unrelated.observer")).test(id)).isFalse();
    assertThat(policy(true, emptySet(), singleton(observer)).test(id)).isTrue();
    assertThat(policy(true, singleton(name), singleton(observer)).test(id)).isFalse();
    assertThat(policy(true, emptySet(), singleton(observer)).test(id(name, Meter.Type.OTHER)))
        .isFalse();
  }

  @Test
  void copiesInputSets() {
    Set<String> registered = new HashSet<>();
    Set<String> kept = new HashSet<>();
    kept.add("jvm.class.count");
    Predicate<Meter.Id> early = policy(true, emptySet(), registered);
    registered.add("jvm.class.count");
    Predicate<Meter.Id> late = policy(true, kept, registered);
    kept.clear();
    assertThat(early.test(id("jvm.class.count", Meter.Type.GAUGE))).isFalse();
    assertThat(late.test(id("jvm.class.count", Meter.Type.GAUGE))).isFalse();
  }

  @Test
  void keptNamesAndUnrelatedMetersAreBridged() {
    Predicate<Meter.Id> policy =
        policy(true, singleton("jvm.classes.loaded"), singleton("jvm.class.count"));
    assertThat(policy.test(id("jvm.classes.loaded", Meter.Type.GAUGE))).isFalse();
    assertThat(policy.test(id("jvm.class.count", Meter.Type.GAUGE))).isTrue();
    assertThat(policy.test(id("jvm.memory.used", Meter.Type.GAUGE))).isFalse();
    assertThat(policy.test(id("custom.metric", Meter.Type.GAUGE))).isFalse();
    assertThat(policy.test(id("hikaricp.connections.max", Meter.Type.GAUGE))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jvm.memory.usage.after.gc",
        "jvm.gc.memory.allocated",
        "jvm.gc.memory.promoted",
        "jvm.gc.max.data.size",
        "jvm.gc.live.data.size",
        "jvm.gc.overhead",
        "jvm.gc.cpu.time",
        "jvm.threads.peak",
        "jvm.threads.started",
        "jvm.threads.deadlocked",
        "jvm.threads.deadlocked.monitor",
        "jvm.threads.virtual.pinned",
        "jvm.threads.virtual.submit.failed",
        "jvm.threads.virtual.parallelism",
        "jvm.threads.virtual.pool.size",
        "jvm.threads.virtual.live",
        "jvm.compilation.time",
        "jvm.info",
        "jvm.memory.used.custom",
        "process.uptime",
        "process.start.time",
        "system.custom"
      })
  void complementaryAndUnknownMetricsRemainBridged(String name) {
    Set<String> observers =
        new HashSet<>(
            asList(
                "jvm.memory.used",
                "jvm.memory.used_after_last_gc",
                "jvm.memory.allocation",
                "jvm.memory.limit",
                "jvm.gc.duration",
                "jvm.thread.count",
                "jvm.cpu.time",
                "jvm.thread.virtual.pinned",
                "jvm.thread.virtual.submit_failed"));
    for (Meter.Type type : Meter.Type.values()) {
      assertThat(policy(true, emptySet(), observers).test(id(name, type))).isFalse();
    }
  }

  @SuppressWarnings("unchecked")
  private static Predicate<Meter.Id> policy(
      boolean enabled, Set<String> kept, Set<String> observers) {
    try {
      // V3's indy instrumentation keeps unexposed helpers in its own loader. Resolve the policy
      // alongside the injected registry, not through the application Micrometer loader.
      Class<?> helper =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.JvmMetricsOwnership",
              true,
              Metrics.globalRegistry.getRegistries().stream()
                  .filter(
                      registry ->
                          registry.getClass().getName().contains("OpenTelemetryMeterRegistry"))
                  .findFirst()
                  .get()
                  .getClass()
                  .getClassLoader());
      Constructor<?> constructor =
          helper.getDeclaredConstructor(boolean.class, Set.class, Set.class);
      constructor.setAccessible(true);
      return (Predicate<Meter.Id>) constructor.newInstance(enabled, kept, observers);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static Meter.Id id(String name, Meter.Type type) {
    return new Meter.Id(name, Tags.of("application", "test"), "classes", null, type);
  }
}
