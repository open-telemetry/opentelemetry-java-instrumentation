/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Collections;
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
            ConfigPropertiesUtil.getBoolean(
                OpenTelemetry.noop(), false, "test", "property", "boolean"))
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
                DeclarativeConfiguration.create(model(property)), false, "foo", "bar"))
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
    assertList(java.util.Arrays.asList("x", "y", "z"));
  }

  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION_TEST_PROPERTY_LIST", value = "a,b,c")
  @Test
  void getList_environmentVariable() {
    assertList(java.util.Arrays.asList("a", "b", "c"));
  }

  @Test
  void getList_none() {
    assertList(Collections.emptyList());
  }

  private static void assertList(java.util.List<String> expected) {
    assertThat(ConfigPropertiesUtil.getList("otel.instrumentation.test.property.list", Collections.emptyList()))
        .isEqualTo(expected);
    assertThat(
            ConfigPropertiesUtil.getList(
                OpenTelemetry.noop(), Collections.emptyList(), "test", "property", "list"))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> listValuesProvider() {
    return Stream.of(
        Arguments.of(java.util.Arrays.asList("a", "b", "c"), java.util.Arrays.asList("a", "b", "c")),
        Arguments.of(java.util.Arrays.asList("single"), java.util.Arrays.asList("single")),
        Arguments.of(Collections.emptyList(), Collections.emptyList()),
        Arguments.of(java.util.Arrays.asList(""), Collections.emptyList()),
        Arguments.of(null, Collections.emptyList()));
  }

  @ParameterizedTest
  @MethodSource("listValuesProvider")
  void getList_declarativeConfig(java.util.List<String> property, java.util.List<String> expected) {
    assertThat(
            ConfigPropertiesUtil.getList(
                DeclarativeConfiguration.create(modelForList(property)), Collections.emptyList(), "foo", "bar"))
        .isEqualTo(expected);
  }

  private static OpenTelemetryConfigurationModel modelForList(java.util.List<String> value) {
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
}
