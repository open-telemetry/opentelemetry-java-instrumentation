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
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpringDeclarativeConfigPropertiesTest {

  private static final Logger yamlLogger =
      Logger.getLogger(YamlDeclarativeConfigProperties.class.getName());

  private final ComponentLoader componentLoader =
      ComponentLoader.forClassLoader(getClass().getClassLoader());

  private final List<LogRecord> warnings = new ArrayList<>();
  private final Handler warningCollector =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          if (record.getLevel().intValue() >= WARNING.intValue()) {
            warnings.add(record);
          }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @BeforeEach
  void captureWarnings() {
    yamlLogger.addHandler(warningCollector);
  }

  @AfterEach
  void verifyNoWarnings() {
    yamlLogger.removeHandler(warningCollector);
    assertThat(warnings).isEmpty();
  }

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

  @Test
  void coercesSpringResolvedStrings() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("bool", "TRUE");
    properties.put("int", "42");
    properties.put("double", "3.14");
    properties.put("text", "abc");
    properties.put("int_max", "2147483647");
    properties.put("int_min", "-2147483648");
    properties.put("int_overflow", "2147483648");
    properties.put("int_underflow", "-2147483649");
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(properties, componentLoader);

    assertThat(config.getBoolean("bool")).isTrue();
    assertThat(config.getInt("int")).isEqualTo(42);
    assertThat(config.getLong("int")).isEqualTo(42L);
    assertThat(config.getDouble("int")).isEqualTo(42.0);
    assertThat(config.getDouble("double")).isEqualTo(3.14);

    // Boolean.parseBoolean rules: anything other than "true" is false
    assertThat(config.getBoolean("text")).isFalse();
    assertThat(config.getInt("text")).isNull();
    assertThat(config.getLong("double")).isNull();
    assertThat(config.getDouble("text")).isNull();

    assertThat(config.isBoolean("bool")).isTrue();
    assertThat(config.isInt("int")).isTrue();
    assertThat(config.isLong("int")).isTrue();
    assertThat(config.isDouble("int")).isTrue();
    assertThat(config.isBoolean("text")).isTrue();
    assertThat(config.isInt("text")).isFalse();

    // Strings are parsed as int, not narrowed from long
    assertThat(config.getInt("int_max")).isEqualTo(Integer.MAX_VALUE);
    assertThat(config.getInt("int_min")).isEqualTo(Integer.MIN_VALUE);
    assertThat(config.getInt("int_overflow")).isNull();
    assertThat(config.isInt("int_overflow")).isFalse();
    assertThat(config.getInt("int_underflow")).isNull();
    assertThat(config.isInt("int_underflow")).isFalse();
    assertThat(config.getLong("int_overflow")).isEqualTo(2147483648L);
  }

  @Test
  void coercesTypedScalars() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("int", 42);
    properties.put("long", 5_000_000_000L);
    properties.put("double", 1.5);
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(properties, componentLoader);

    assertThat(config.getDouble("int")).isEqualTo(42.0);
    assertThat(config.getDouble("long")).isEqualTo(5_000_000_000.0);
    assertThat(config.getLong("long")).isEqualTo(5_000_000_000L);
    // narrowed with Long.intValue()
    assertThat(config.getInt("long")).isEqualTo((int) 5_000_000_000L);
    assertThat(config.isInt("long")).isTrue();
    assertThat(config.getBoolean("int")).isNull();
    assertThat(config.getLong("double")).isNull();
  }

  @Test
  void missingAndNullValuesForEveryType() {
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(singletonMap("null", null), componentLoader);

    for (String key : asList("null", "missing")) {
      assertThat(config.getBoolean(key)).isNull();
      assertThat(config.getInt(key)).isNull();
      assertThat(config.getLong(key)).isNull();
      assertThat(config.getDouble(key)).isNull();
      assertThat(config.getScalarList(key, Long.class)).isNull();
    }
  }

  @Test
  void getScalarListCoercesElements() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("numbers", asList("1", "2"));
    properties.put("typed", asList(1, 2L, 3.5));
    properties.put("mixed", asList("1", "x"));
    properties.put("words", singletonList("x"));
    properties.put("flags", asList("true", false, "x"));
    properties.put("bools_and_numbers", asList(true, 42, false));
    properties.put("numbers_only", asList(1, 2));
    properties.put("empty", emptyList());
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(properties, componentLoader);

    assertThat(config.getScalarList("numbers", Long.class)).containsExactly(1L, 2L);
    assertThat(config.getScalarList("numbers", Double.class)).containsExactly(1.0, 2.0);
    assertThat(config.getScalarList("typed", Double.class)).containsExactly(1.0, 2.0, 3.5);
    assertThat(config.getScalarList("typed", Long.class)).containsExactly(1L, 2L);
    assertThat(config.getScalarList("flags", Boolean.class)).containsExactly(true, false, false);
    // only String elements are parsed as booleans; numbers are dropped, as in getBoolean
    assertThat(config.getScalarList("bools_and_numbers", Boolean.class))
        .containsExactly(true, false);
    assertThat(config.getScalarList("numbers_only", Boolean.class)).isNull();
    // elements which cannot be converted are dropped, and an all-dropped list is null
    assertThat(config.getScalarList("mixed", Long.class)).containsExactly(1L);
    assertThat(config.getScalarList("words", Long.class)).isNull();
    assertThat(config.getScalarList("empty", Long.class)).isEmpty();
  }

  @Test
  void coercesNestedProperties() {
    Map<String, Object> child = new HashMap<>();
    child.put("enabled", "true");
    child.put("ports", asList(8080, "8081"));
    Map<String, Object> listItem = new HashMap<>();
    listItem.put("port", "8080");
    Map<String, Object> properties = new HashMap<>();
    properties.put("child", child);
    properties.put("items", singletonList(listItem));
    DeclarativeConfigProperties config =
        SpringDeclarativeConfigProperties.create(properties, componentLoader);

    DeclarativeConfigProperties childConfig = config.getStructured("child");
    assertThat(childConfig).isNotNull();
    assertThat(childConfig.getBoolean("enabled")).isTrue();
    assertThat(childConfig.getScalarList("ports", Long.class)).containsExactly(8080L, 8081L);

    List<DeclarativeConfigProperties> items = config.getStructuredList("items");
    assertThat(items).hasSize(1);
    assertThatThrownBy(() -> items.add(childConfig))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(items.get(0).getInt("port")).isEqualTo(8080);

    assertThat(config.get("child").getBoolean("enabled")).isTrue();
    assertThat(config.getPropertyKeys()).containsExactlyInAnyOrder("child", "items");
  }
}
