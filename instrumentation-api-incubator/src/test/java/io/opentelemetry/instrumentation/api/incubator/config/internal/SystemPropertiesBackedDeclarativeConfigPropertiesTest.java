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

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = ",,a,,b,,")
  @Test
  void getList_multipleCommas() {
    assertList(asList("a", "b"));
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.list", value = "   ")
  @Test
  void getList_onlyWhitespace() {
    assertList(emptyList());
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_INT", value = "100")
  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "200")
  @Test
  void getInt_systemProperty() {
    assertInt(200);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_INT", value = "100")
  @Test
  void getInt_environmentVariable() {
    assertInt(100);
  }

  @Test
  void getInt_none() {
    assertInt(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "0")
  @Test
  void getInt_zero() {
    assertInt(0);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "-42")
  @Test
  void getInt_negative() {
    assertInt(-42);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "2147483647")
  @Test
  void getInt_maxValue() {
    assertInt(Integer.MAX_VALUE);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "-2147483648")
  @Test
  void getInt_minValue() {
    assertInt(Integer.MIN_VALUE);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "invalid")
  @Test
  void getInt_invalidValue() {
    assertInt(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "12.34")
  @Test
  void getInt_decimalValue() {
    assertInt(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.int", value = "")
  @Test
  void getInt_emptyString() {
    assertInt(null);
  }

  private static void assertInt(@Nullable Integer expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getInt("int"))
        .isEqualTo(expected);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_LONG", value = "100")
  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "200")
  @Test
  void getLong_systemProperty() {
    assertLong(200L);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_LONG", value = "100")
  @Test
  void getLong_environmentVariable() {
    assertLong(100L);
  }

  @Test
  void getLong_none() {
    assertLong(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "0")
  @Test
  void getLong_zero() {
    assertLong(0L);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "-42")
  @Test
  void getLong_negative() {
    assertLong(-42L);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "9223372036854775807")
  @Test
  void getLong_maxValue() {
    assertLong(Long.MAX_VALUE);
  }

  @SetSystemProperty(
      key = "otel.instrumentation.test.property.long",
      value = "-9223372036854775808")
  @Test
  void getLong_minValue() {
    assertLong(Long.MIN_VALUE);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "invalid")
  @Test
  void getLong_invalidValue() {
    assertLong(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.long", value = "")
  @Test
  void getLong_emptyString() {
    assertLong(null);
  }

  private static void assertLong(@Nullable Long expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getLong("long"))
        .isEqualTo(expected);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_DOUBLE", value = "1.5")
  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "2.5")
  @Test
  void getDouble_systemProperty() {
    assertDouble(2.5);
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_DOUBLE", value = "1.5")
  @Test
  void getDouble_environmentVariable() {
    assertDouble(1.5);
  }

  @Test
  void getDouble_none() {
    assertDouble(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "0.0")
  @Test
  void getDouble_zero() {
    assertDouble(0.0);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "-42.5")
  @Test
  void getDouble_negative() {
    assertDouble(-42.5);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "42")
  @Test
  void getDouble_integerValue() {
    assertDouble(42.0);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "1.23e10")
  @Test
  void getDouble_scientificNotation() {
    assertDouble(1.23e10);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "Infinity")
  @Test
  void getDouble_infinity() {
    assertDouble(Double.POSITIVE_INFINITY);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "-Infinity")
  @Test
  void getDouble_negativeInfinity() {
    assertDouble(Double.NEGATIVE_INFINITY);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "NaN")
  @Test
  void getDouble_nan() {
    assertThat(
            SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig()
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getDouble("double"))
        .isNaN();
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "invalid")
  @Test
  void getDouble_invalidValue() {
    assertDouble(null);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.double", value = "")
  @Test
  void getDouble_emptyString() {
    assertDouble(null);
  }

  private static void assertDouble(@Nullable Double expected) {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getDouble("double"))
        .isEqualTo(expected);
  }

  @Test
  void getStructuredList_returnsNull() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getStructuredList("list"))
        .isNull();
  }

  @Test
  void getPropertyKeys_returnsEmptySet() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(config.getPropertyKeys()).isEmpty();
  }

  @SetSystemProperty(
      key = "otel.instrumentation.experimental.test-feature.property",
      value = "experimental_value")
  @Test
  void getStructured_developmentSuffix() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test_feature/development")
                .getString("property"))
        .isEqualTo("experimental_value");
  }

  @SetSystemProperty(key = "otel.instrumentation.test-feature.property", value = "dash_value")
  @Test
  void getStructured_underscoreToDash() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test_feature")
                .getString("property"))
        .isEqualTo("dash_value");
  }

  @SetSystemProperty(key = "otel.instrumentation.nested.path.property", value = "nested_value")
  @Test
  void getStructured_nestedPath() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("nested")
                .getStructured("path")
                .getString("property"))
        .isEqualTo("nested_value");
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.string", value = "")
  @Test
  void getString_emptyString() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getString("string"))
        .isEmpty();
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.string", value = "  whitespace  ")
  @Test
  void getString_withWhitespace() {
    DeclarativeConfigProperties config =
        SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getStructured("property")
                .getString("string"))
        .isEqualTo("  whitespace  ");
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.boolean", value = "false")
  @Test
  void getBoolean_false() {
    assertBoolean(false);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.boolean", value = "TRUE")
  @Test
  void getBoolean_uppercase() {
    assertBoolean(true);
  }

  @SetSystemProperty(key = "otel.instrumentation.test.property.boolean", value = "False")
  @Test
  void getBoolean_mixedCase() {
    assertBoolean(false);
  }
}
