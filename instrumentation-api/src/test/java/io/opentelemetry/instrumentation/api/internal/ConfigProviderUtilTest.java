/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

class ConfigProviderUtilTest {
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
    assertString(null);
  }

  private static void assertString(@Nullable String expected) {
    assertThat(
            InstrumentationConfigUtil.<String>getOrNull(
                ConfigProviderUtil.getConfigProvider(GlobalOpenTelemetry.get()),
                config -> config.getString("string"),
                "java",
                "test",
                "property"))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> stringValuesProvider() {
    return Stream.of(
        Arguments.of("value1", "value1"),
        Arguments.of("", ""),
        Arguments.of(null, null),
        Arguments.of(123, null), // no type coercion in declarative config
        Arguments.of(true, null)); // no type coercion in declarative config
  }

  @ParameterizedTest
  @MethodSource("stringValuesProvider")
  void getString_declarativeConfig(Object property, String expected) {
    assertThat(
            InstrumentationConfigUtil.<String>getOrNull(
                ConfigProviderUtil.getConfigProvider(
                    DeclarativeConfiguration.create(model(property))),
                config -> config.getString("bar"),
                "java",
                "foo"))
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
    assertBoolean(false);
  }

  private static void assertBoolean(boolean expected) {
    assertThat(
            Optional.ofNullable(
                    InstrumentationConfigUtil.<Boolean>getOrNull(
                        ConfigProviderUtil.getConfigProvider(GlobalOpenTelemetry.get()),
                        config -> config.getBoolean("boolean"),
                        "java",
                        "test",
                        "property"))
                .orElse(false))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> booleanValuesProvider() {
    return Stream.of(
        Arguments.of(true, true),
        Arguments.of(false, false),
        Arguments.of("invalid", null),
        Arguments.of("true", null), // no type coercion in declarative config
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("booleanValuesProvider")
  void getBoolean_declarativeConfig(@Nullable Object property, @Nullable Boolean expected) {
    assertThat(
            InstrumentationConfigUtil.<Boolean>getOrNull(
                ConfigProviderUtil.getConfigProvider(
                    DeclarativeConfiguration.create(model(property))),
                config -> config.getBoolean("bar"),
                "java",
                "foo"))
        .isEqualTo(expected);
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
    assertThat(
            InstrumentationConfigUtil.<List<String>>getOrNull(
                ConfigProviderUtil.getConfigProvider(OpenTelemetry.noop()),
                config -> config.getScalarList("list", String.class),
                "java",
                "test",
                "property"))
        .isEqualTo(expected);
  }

  public static Stream<Arguments> listValuesProvider() {
    return Stream.of(
        Arguments.of(asList("a", "b", "c"), asList("a", "b", "c")),
        Arguments.of(singletonList("single"), singletonList("single")),
        Arguments.of(emptyList(), emptyList()),
        Arguments.of("invalid", null),
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("listValuesProvider")
  void getList_declarativeConfig(@Nullable Object property, @Nullable List<String> expected) {
    assertThat(
            InstrumentationConfigUtil.<List<String>>getOrNull(
                ConfigProviderUtil.getConfigProvider(
                    DeclarativeConfiguration.create(model(property))),
                config -> config.getScalarList("bar", String.class),
                "java",
                "foo"))
        .isEqualTo(expected);
  }

  private static OpenTelemetryConfigurationModel model(@Nullable Object value) {
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
