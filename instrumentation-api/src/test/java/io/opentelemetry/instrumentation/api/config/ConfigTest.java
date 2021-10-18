/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

// suppress duration unit check, e.g. ofMillis(5000) -> ofSeconds(5)
@SuppressWarnings("CanonicalDuration")
class ConfigTest {
  @Test
  void shouldGetString() {
    Config config = Config.builder().addProperty("prop.string", "some text").build();

    assertEquals("some text", config.getString("prop.string"));
    assertEquals("some text", config.getString("prop.string", "default"));
    assertNull(config.getString("prop.missing"));
    assertEquals("default", config.getString("prop.missing", "default"));
  }

  @Test
  void shouldGetBoolean() {
    Config config = Config.builder().addProperty("prop.boolean", "true").build();

    assertTrue(config.getBoolean("prop.boolean"));
    assertTrue(config.getBoolean("prop.boolean", false));
    assertNull(config.getBoolean("prop.missing"));
    assertFalse(config.getBoolean("prop.missing", false));
  }

  @Test
  void shouldGetInt() {
    Config config =
        Config.builder().addProperty("prop.int", "12").addProperty("prop.wrong", "twelve").build();

    assertEquals(12, config.getInt("prop.int"));
    assertEquals(12, config.getInt("prop.int", 1000));
    assertEquals(1000, config.getInt("prop.wrong", 1000));
    assertNull(config.getInt("prop.missing"));
    assertEquals(1000, config.getInt("prop.missing", 1000));
  }

  @Test
  void shouldFailOnInvalidInt() {
    Config config = Config.builder().addProperty("prop.wrong", "twelve").build();

    assertThrows(ConfigParsingException.class, () -> config.getInt("prop.wrong"));
  }

  @Test
  void shouldGetLong() {
    Config config =
        Config.builder().addProperty("prop.long", "12").addProperty("prop.wrong", "twelve").build();

    assertEquals(12, config.getLong("prop.long"));
    assertEquals(12, config.getLong("prop.long", 1000));
    assertEquals(1000, config.getLong("prop.wrong", 1000));
    assertNull(config.getLong("prop.missing"));
    assertEquals(1000, config.getLong("prop.missing", 1000));
  }

  @Test
  void shouldFailOnInvalidLong() {
    Config config = Config.builder().addProperty("prop.wrong", "twelve").build();

    assertThrows(ConfigParsingException.class, () -> config.getLong("prop.wrong"));
  }

  @Test
  void shouldGetDouble() {
    Config config =
        Config.builder()
            .addProperty("prop.double", "12.345")
            .addProperty("prop.wrong", "twelve point something")
            .build();

    assertEquals(12.345, config.getDouble("prop.double"));
    assertEquals(12.345, config.getDouble("prop.double", 99.99));
    assertEquals(99.99, config.getDouble("prop.wrong", 99.99));
    assertNull(config.getDouble("prop.missing"));
    assertEquals(99.99, config.getDouble("prop.missing", 99.99));
  }

  @Test
  void shouldFailOnInvalidDouble() {
    Config config = Config.builder().addProperty("prop.wrong", "twelve point something").build();

    assertThrows(ConfigParsingException.class, () -> config.getDouble("prop.wrong"));
  }

  @Test
  void shouldGetDuration_defaultUnit() {
    Config config =
        Config.builder()
            .addProperty("prop.duration", "5000")
            .addProperty("prop.wrong", "hundred days")
            .build();

    assertEquals(Duration.ofMillis(5000), config.getDuration("prop.duration"));
    assertEquals(Duration.ofMillis(5000), config.getDuration("prop.duration", Duration.ZERO));
    assertEquals(Duration.ZERO, config.getDuration("prop.wrong", Duration.ZERO));
    assertNull(config.getDuration("prop.missing"));
    assertEquals(Duration.ZERO, config.getDuration("prop.missing", Duration.ZERO));
  }

  @Test
  void shouldFailOnInvalidDuration() {
    Config config = Config.builder().addProperty("prop.wrong", "hundred days").build();

    assertThrows(ConfigParsingException.class, () -> config.getDuration("prop.wrong"));
  }

  @Test
  void shouldGetDuration_variousUnits() {
    Config config = Config.builder().addProperty("prop.duration", "100ms").build();
    assertEquals(Duration.ofMillis(100), config.getDuration("prop.duration"));

    config = Config.builder().addProperty("prop.duration", "100s").build();
    assertEquals(Duration.ofSeconds(100), config.getDuration("prop.duration"));

    config = Config.builder().addProperty("prop.duration", "100m").build();
    assertEquals(Duration.ofMinutes(100), config.getDuration("prop.duration"));

    config = Config.builder().addProperty("prop.duration", "100h").build();
    assertEquals(Duration.ofHours(100), config.getDuration("prop.duration"));

    config = Config.builder().addProperty("prop.duration", "100d").build();
    assertEquals(Duration.ofDays(100), config.getDuration("prop.duration"));
  }

  @Test
  void shouldGetList() {
    Config config = Config.builder().addProperty("prop.list", "one, two ,three").build();

    assertEquals(asList("one", "two", "three"), config.getList("prop.list"));
    assertEquals(
        asList("one", "two", "three"), config.getList("prop.list", singletonList("default")));
    assertTrue(config.getList("prop.missing").isEmpty());
    assertEquals(
        singletonList("default"), config.getList("prop.missing", singletonList("default")));
  }

  @Test
  void shouldGetMap() {
    Config config =
        Config.builder()
            .addProperty("prop.map", "one=1, two=2")
            .addProperty("prop.wrong", "one=1, but not two!")
            .addProperty("prop.trailing", "one=1,")
            .build();

    assertThat(config.getMap("prop.map")).containsOnly(entry("one", "1"), entry("two", "2"));
    assertThat(config.getMap("prop.map", singletonMap("three", "3")))
        .containsOnly(entry("one", "1"), entry("two", "2"));
    assertThat(config.getMap("prop.wrong", singletonMap("three", "3")))
        .containsOnly(entry("three", "3"));
    assertThat(config.getMap("prop.missing")).isEmpty();
    assertThat(config.getMap("prop.missing", singletonMap("three", "3")))
        .containsOnly(entry("three", "3"));
    assertThat(config.getMap("prop.trailing")).containsOnly(entry("one", "1"));
  }

  @Test
  void shouldFailOnInvalidMap() {
    Config config = Config.builder().addProperty("prop.wrong", "one=1, but not two!").build();

    assertThrows(ConfigParsingException.class, () -> config.getMap("prop.wrong"));
  }

  @ParameterizedTest
  @ArgumentsSource(AgentDebugParams.class)
  void shouldCheckIfAgentDebugModeIsEnabled(String propertyValue, boolean expected) {
    Config config = Config.builder().addProperty("otel.javaagent.debug", propertyValue).build();

    assertEquals(expected, config.isAgentDebugEnabled());
  }

  private static class AgentDebugParams implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("true", true), Arguments.of("blather", false), Arguments.of(null, false));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(InstrumentationEnabledParams.class)
  void shouldCheckIfInstrumentationIsEnabled(
      List<String> names, boolean defaultEnabled, boolean expected) {
    Config config =
        Config.builder()
            .addProperty("otel.instrumentation.order.enabled", "true")
            .addProperty("otel.instrumentation.test-prop.enabled", "true")
            .addProperty("otel.instrumentation.disabled-prop.enabled", "false")
            .addProperty("otel.instrumentation.test-env.enabled", "true")
            .addProperty("otel.instrumentation.disabled-env.enabled", "false")
            .build();

    assertEquals(expected, config.isInstrumentationEnabled(new TreeSet<>(names), defaultEnabled));
  }

  private static class InstrumentationEnabledParams implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(emptyList(), true, true),
          Arguments.of(emptyList(), false, false),
          Arguments.of(singletonList("invalid"), true, true),
          Arguments.of(singletonList("invalid"), false, false),
          Arguments.of(singletonList("test-prop"), false, true),
          Arguments.of(singletonList("test-env"), false, true),
          Arguments.of(singletonList("disabled-prop"), true, false),
          Arguments.of(singletonList("disabled-env"), true, false),
          Arguments.of(asList("other", "test-prop"), false, true),
          Arguments.of(asList("other", "test-env"), false, true),
          Arguments.of(singletonList("order"), false, true),
          Arguments.of(asList("test-prop", "disabled-prop"), false, true),
          Arguments.of(asList("disabled-env", "test-env"), false, true),
          Arguments.of(asList("test-prop", "disabled-prop"), true, false),
          Arguments.of(asList("disabled-env", "test-env"), true, false));
    }
  }
}
