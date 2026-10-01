/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JvmMetricsOwnershipTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void initializeBridgeHelpers() {
    // Helpers live in the application loader only after Micrometer instrumentation applies.
    assertThat(Metrics.globalRegistry.getRegistries()).isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({
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
    assertThat(policy(true, emptySet(), singleton(observer)).test(id(name, Meter.Type.TIMER)))
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
