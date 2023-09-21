/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionMapTest {

  @ParameterizedTest
  @MethodSource("providesMapsArguments")
  void testMaps(Map<String, String> first, Map<String, String> second) {
    UnionMap<String, String> union = new UnionMap<>(first, second);

    assertThat(union.get("cat")).isEqualTo("meow");
    assertThat(union.get("dog")).isEqualTo("bark");
    assertThat(union.get("foo")).isEqualTo("bar");
    assertThat(union.get("hello")).isEqualTo("world");
    assertThat(union.get("giraffe")).isNull();

    assertThat(union.isEmpty()).isFalse();
    assertThat(union.size()).isEqualTo(4);
    assertThat(union.containsKey("cat")).isTrue();
    assertThat(union.containsKey("dog")).isTrue();
    assertThat(union.containsKey("foo")).isTrue();
    assertThat(union.containsKey("hello")).isTrue();
    assertThat(union.containsKey("giraffe")).isFalse();

    Set<Map.Entry<String, String>> set = union.entrySet();
    assertThat(set.isEmpty()).isFalse();
    assertThat(set.size()).isEqualTo(4);
    assertThat(set.toArray().length).isEqualTo(4);
  }

  private static Stream<Arguments> providesMapsArguments() {
    ImmutableMap<String, String> firstArg =
        ImmutableMap.of(
            "cat", "meow",
            "dog", "bark");

    return Stream.of(
        Arguments.of(
            firstArg,
            ImmutableMap.of(
                "foo", "bar",
                "hello", "world")),
        Arguments.of(
            firstArg,
            ImmutableMap.of(
                "foo", "bar",
                "hello", "world",
                "cat", "moo")));
  }

  @Test
  void testBothEmpty() {
    UnionMap<String, String> union = new UnionMap<>(Collections.emptyMap(), Collections.emptyMap());

    assertThat(union.isEmpty()).isTrue();
    assertThat(union.size()).isEqualTo(0);
    assertThat(union.get("cat")).isNull();

    Set<Map.Entry<String, String>> set = union.entrySet();
    assertThat(set.isEmpty()).isTrue();
    assertThat(set.size()).isEqualTo(0);

    assertThat(set.toArray().length).isEqualTo(0);
  }

  @ParameterizedTest
  @MethodSource("providesOneEmptyArguments")
  void testOneEmpty(Map<String, String> first, Map<String, String> second) {
    UnionMap<String, String> union = new UnionMap<>(first, second);

    assertThat(union.isEmpty()).isFalse();
    assertThat(union.size()).isEqualTo(1);
    assertThat(union.get("cat")).isEqualTo("meow");
    assertThat(union.get("dog")).isNull();

    Set<Map.Entry<String, String>> set = union.entrySet();
    assertThat(set.isEmpty()).isFalse();
    assertThat(set.size()).isEqualTo(1);

    assertThat(set.toArray().length).isEqualTo(1);
  }

  private static Stream<Arguments> providesOneEmptyArguments() {
    return Stream.of(
        Arguments.of(ImmutableMap.of("cat", "meow"), Collections.emptyMap()),
        Arguments.of(Collections.emptyMap(), ImmutableMap.of("cat", "meow")));
  }
}
