/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpringDeclarativeConfigPropertiesTest {

  private final ComponentLoader componentLoader =
      ComponentLoader.forClassLoader(getClass().getClassLoader());

  @ParameterizedTest
  @MethodSource("scalarValues")
  void getStringCoercesScalars(Object value, String expected) {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(singletonMap("value", value), componentLoader);

    assertThat(config.getString("value")).isEqualTo(expected);
    assertThat(config.isString("value")).isTrue();
  }

  static Stream<Arguments> scalarValues() {
    return Stream.of(
        arguments("text", "text"),
        arguments("", ""),
        arguments(true, "true"),
        arguments(false, "false"),
        arguments(42, "42"),
        arguments(2147483648L, "2147483648"),
        arguments(3.14, "3.14"));
  }

  @ParameterizedTest
  @MethodSource("nonScalarValues")
  void getStringRejectsNonScalars(Object value) {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(singletonMap("value", value), componentLoader);

    assertThat(config.getString("value")).isNull();
    assertThat(config.getString("value", "default")).isEqualTo("default");
    assertThat(config.isString("value")).isFalse();
  }

  static Stream<Object> nonScalarValues() {
    return Stream.of(
        emptyList(),
        asList("a", "b"),
        asList(42, true, 3.14),
        emptyMap(),
        singletonMap("key", "value"),
        singletonList(singletonMap("key", "value")));
  }

  @Test
  void getStringRejectsNullAndMissingValues() {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(singletonMap("value", null), componentLoader);

    assertThat(config.getString("value")).isNull();
    assertThat(config.isString("value")).isFalse();
    assertThat(config.getString("missing")).isNull();
    assertThat(config.isString("missing")).isFalse();
  }

  @Test
  void getScalarListCoercesScalarsToStrings() {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(
            singletonMap("value", asList("text", "", true, false, 42, 2147483648L, 3.14)),
            componentLoader);

    assertThat(config.getScalarList("value", String.class))
        .containsExactly("text", "", "true", "false", "42", "2147483648", "3.14");
  }

  @Test
  void getScalarListPreservesEmptyLists() {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(
            singletonMap("value", emptyList()), componentLoader);

    assertThat(config.getScalarList("value", String.class)).isEmpty();
  }
}
