/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

class ConfigPropertiesUtilTest {

  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @SetSystemProperty(key = "test.property.string", value = "sys")
  @Test
  void getString_systemProperty() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isEqualTo("sys");
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @Test
  void getString_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isEqualTo("env");
  }

  @Test
  void getString_none() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isNull();
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_STRING", value = "env_value")
  @SetSystemProperty(key = "otel.instrumentation.test.property.string", value = "sys_value")
  @Test
  void getString_withOpenTelemetry_systemProperty() {
    assertString("sys_value");
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_STRING", value = "env_value")
  @Test
  void getString_withOpenTelemetry_environmentVariable() {
    assertString("env_value");
  }

  @Test
  void getString_withOpenTelemetry_none() {
    assertString("default_value");
  }

  private static void assertString(String expected) {
    assertThat(
            ConfigPropertiesUtil.getString(OpenTelemetry.noop(), "test", "property", "string")
                .orElse("default_value"))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> stringValuesProvider() {
    return Stream.of(
        Arguments.of("value1", "value1"),
        Arguments.of("", ""),
        Arguments.of(null, "default_value"),
        Arguments.of(123, "default_value"), // no type coercion in declarative config
        Arguments.of(true, "default_value")); // no type coercion in declarative config
  }

  @ParameterizedTest
  @MethodSource("stringValuesProvider")
  void getString_declarativeConfig(Object property, String expected) {
    OpenTelemetry openTelemetry = DeclarativeConfiguration.create(model(property));
    assertThat(ConfigPropertiesUtil.getString(openTelemetry, "foo", "bar").orElse("default_value"))
        .isEqualTo(expected);
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "12")
  @SetSystemProperty(key = "test.property.int", value = "42")
  @Test
  void getInt_systemProperty() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(42);
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "12")
  @Test
  void getInt_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(12);
  }

  @Test
  void getInt_none() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(-1);
  }

  @SetSystemProperty(key = "test.property.int", value = "not a number")
  @Test
  void getInt_invalidNumber() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(-1);
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
    assertBoolean(false);
  }

  private static void assertBoolean(boolean expected) {
    assertThat(ConfigPropertiesUtil.getBoolean("otel.instrumentation.test.property.boolean", false))
        .isEqualTo(expected);
    assertThat(
            ConfigPropertiesUtil.getBoolean(OpenTelemetry.noop(), "test", "property", "boolean")
                .orElse(false))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> booleanValuesProvider() {
    return Stream.of(
        Arguments.of(true, true),
        Arguments.of(false, false),
        Arguments.of("invalid", false),
        Arguments.of("true", false), // no type coercion in declarative config
        Arguments.of(null, false));
  }

  @ParameterizedTest
  @MethodSource("booleanValuesProvider")
  void getBoolean_declarativeConfig(Object property, boolean expected) {
    assertThat(
            ConfigPropertiesUtil.getBoolean(
                    DeclarativeConfiguration.create(model(property)), "foo", "bar")
                .orElse(false))
        .isEqualTo(expected);
  }

  private static OpenTelemetryConfigurationModel model(Object value) {
    return new DeclarativeConfigurationBuilder()
        .customizeModel(
            new OpenTelemetryConfigurationModel()
                .withFileFormat("1.0-rc.1")
                .withInstrumentationDevelopment(
                    new InstrumentationModel()
                        .withJava(
                            new ExperimentalLanguageSpecificInstrumentationModel()
                                .withAdditionalProperty(
                                    "foo", Collections.singletonMap("bar", value)))));
  }

  @Test
  void toSystemProperty() {
    assertThat(ConfigPropertiesUtil.toSystemProperty(new String[] {"a_b", "c", "d"}))
        .isEqualTo("otel.instrumentation.a-b.c.d");
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
    assertList(emptyList());
  }

  private static void assertList(List<String> expected) {
    assertThat(ConfigPropertiesUtil.getList(OpenTelemetry.noop(), "test", "property", "list"))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> listValuesProvider() {
    return Stream.of(
        Arguments.of(asList("a", "b", "c"), asList("a", "b", "c")),
        Arguments.of(singletonList("single"), singletonList("single")),
        Arguments.of(emptyList(), emptyList()),
        Arguments.of("invalid", emptyList()),
        Arguments.of(null, emptyList()));
  }

  @ParameterizedTest
  @MethodSource("listValuesProvider")
  void getList_declarativeConfig(Object property, List<String> expected) {
    assertThat(
            ConfigPropertiesUtil.getList(
                DeclarativeConfiguration.create(model(property)), "foo", "bar"))
        .isEqualTo(expected);
  }
}
