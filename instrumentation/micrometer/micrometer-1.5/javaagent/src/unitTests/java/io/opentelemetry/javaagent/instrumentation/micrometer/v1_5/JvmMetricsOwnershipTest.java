/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JvmMetricsOwnershipTest {

  @ParameterizedTest
  @CsvSource({
    "jvm.memory.used, GAUGE",
    "jvm.memory.committed, GAUGE",
    "jvm.memory.max, GAUGE",
    "jvm.memory.limit, GAUGE",
    "jvm.threads.live, GAUGE",
    "jvm.threads.daemon, GAUGE",
    "jvm.threads.states, GAUGE",
    "jvm.thread.count, GAUGE",
    "system.cpu.count, GAUGE",
    "jvm.cpu.count, GAUGE",
    "process.cpu.usage, GAUGE",
    "jvm.cpu.recent_utilization, GAUGE",
    "process.cpu.time, COUNTER",
    "jvm.cpu.time, COUNTER",
    "jvm.gc.pause, TIMER",
    "jvm.gc.concurrent.phase.time, TIMER",
    "jvm.classes.loaded, GAUGE",
    "jvm.class.count, GAUGE",
    "jvm.classes.loaded.count, COUNTER",
    "jvm.class.loaded, COUNTER",
    "jvm.classes.unloaded, COUNTER",
    "jvm.class.unloaded, COUNTER"
  })
  void stableMetricsAreAlwaysSuppressed(String name, Meter.Type type) {
    assertThat(new JvmMetricsOwnership(false).test(id(name, type))).isTrue();
    assertThat(new JvmMetricsOwnership(true).test(id(name, type))).isTrue();
    assertThat(new JvmMetricsOwnership(true).test(id(name, Meter.Type.OTHER))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "jvm.buffer.count, GAUGE",
    "jvm.buffer.memory.used, GAUGE",
    "jvm.buffer.total.capacity, GAUGE",
    "system.cpu.usage, GAUGE",
    "system.load.average.1m, GAUGE",
    "process.files.open, GAUGE",
    "process.files.max, GAUGE"
  })
  void experimentalMetricsAreSuppressedOnlyWithExperimentalRuntimeTelemetry(
      String name, Meter.Type type) {
    assertThat(new JvmMetricsOwnership(false).test(id(name, type))).isFalse();
    assertThat(new JvmMetricsOwnership(true).test(id(name, type))).isTrue();
    assertThat(new JvmMetricsOwnership(true).test(id(name, Meter.Type.OTHER))).isFalse();
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
        "system.custom",
        "custom.metric",
        "hikaricp.connections.max"
      })
  void complementaryAndUnknownMetricsRemainBridged(String name) {
    for (Meter.Type type : Meter.Type.values()) {
      assertThat(new JvmMetricsOwnership(true).test(id(name, type))).isFalse();
    }
  }

  private static Meter.Id id(String name, Meter.Type type) {
    return new Meter.Id(name, Tags.of("application", "test"), "classes", null, type);
  }
}
