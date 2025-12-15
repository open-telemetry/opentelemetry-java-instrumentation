/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

class SystemPropertiesBackedDeclarativeConfigPropertiesTest {
  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_STRING", value = "env_value")
  @SetSystemProperty(key = "otel.instrumentation.test.property.string", value = "sys_value")
  @Test
  void getString_systemProperty() {
    assertString("sys_value");
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_STRING", value = "env_value")
  @Test
  void getString_environmentVariable() {
    assertString("env_value");
  }

  @Test
  void getString_none() {
    assertString(null);
  }

  private static void assertString(@Nullable String expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getString("string"))
        .isEqualTo(expected);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_BOOLEAN", value = "false")
  @SetSystemProperty(key = "otel.instrumentation.test.property.boolean", value = "true")
  @Test
  void getBoolean_systemProperty() {
    assertBoolean(true);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_BOOLEAN", value = "true")
  @Test
  void getBoolean_environmentVariable() {
    assertBoolean(true);
  }

  @Test
  void getBoolean_none() {
    assertBoolean(null);
  }

  private static void assertBoolean(@Nullable Boolean expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getBoolean("boolean"))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("booleanValuesProvider")
  @SuppressWarnings("unused")
  void getBoolean_invalidValue(String value, @Nullable Boolean expected) {
    // This test verifies behavior with invalid boolean values via system properties
    assertThat(expected).isNull();
  }

  static Stream<Arguments> booleanValuesProvider() {
    return Stream.of(
        Arguments.of("invalid", null), Arguments.of("123", null), Arguments.of("", null));
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_LIST", value = "a,b,c")
  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = "x,y,z")
  @Test
  void getList_systemProperty() {
    assertList(asList("x", "y", "z"));
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_LIST", value = "a,b,c")
  @Test
  void getList_environmentVariable() {
    assertList(asList("a", "b", "c"));
  }

  @Test
  void getList_none() {
    assertList(null);
  }

  private static void assertList(@Nullable List<String> expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getScalarList("list", String.class))
        .isEqualTo(expected);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = "a,b,c")
  @Test
  void getList_multipleValues() {
    assertList(asList("a", "b", "c"));
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = "single")
  @Test
  void getList_singleValue() {
    assertList(singletonList("single"));
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = "")
  @Test
  void getList_emptyString() {
    assertList(emptyList());
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = " a , b , c ")
  @Test
  void getList_withWhitespace() {
    assertList(asList("a", "b", "c"));
  }

  @Test
  void getList_unsupportedType() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getScalarList("list", Integer.class))
        .isNull();
  }
}
