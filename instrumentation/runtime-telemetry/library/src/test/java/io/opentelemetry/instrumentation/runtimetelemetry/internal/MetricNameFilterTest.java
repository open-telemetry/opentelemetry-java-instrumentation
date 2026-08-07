/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetricNameFilterTest {

  @Test
  void matchesExactNamesAndPrefixes() {
    MetricNameFilter filter = MetricNameFilter.create(asList("jvm.cpu.time", "jvm.memory.*"));

    assertThat(filter.test("jvm.cpu.time")).isTrue();
    assertThat(filter.test("jvm.cpu.count")).isFalse();
    assertThat(filter.test("jvm.memory.used")).isTrue();
    assertThat(filter.test("jvm.memory.")).isTrue();
    assertThat(filter.test("jvm.memory")).isFalse();
  }

  @Test
  void matchesAllWildcard() {
    MetricNameFilter filter = MetricNameFilter.create(singletonList("*"));

    assertThat(filter.test("jvm.cpu.time")).isTrue();
    assertThat(filter.test("")).isTrue();
  }

  @Test
  void emptyPatternsMatchNothing() {
    MetricNameFilter filter = MetricNameFilter.create(emptyList());

    assertThat(filter.test("jvm.cpu.time")).isFalse();
  }

  @Test
  void rejectsUnsupportedPatterns() {
    assertThatThrownBy(() -> MetricNameFilter.create(singletonList("")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MetricNameFilter.create(singletonList("jvm.*.time")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MetricNameFilter.create(singletonList("jvm.cpu**")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
