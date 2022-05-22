/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

// suppress duration unit check, e.g. ofMillis(5000) -> ofSeconds(5)
@SuppressWarnings({"CanonicalDuration"})
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

    assertTrue(config.getBoolean("prop.boolean", false));
    assertFalse(config.getBoolean("prop.missing", false));
  }

  @Test
  void shouldGetInt() {
    Config config =
        Config.builder().addProperty("prop.int", "12").addProperty("prop.wrong", "twelve").build();

    assertEquals(12, config.getInt("prop.int", 1000));
    assertEquals(1000, config.getInt("prop.wrong", 1000));
    assertEquals(1000, config.getInt("prop.missing", 1000));
  }

  @Test
  void shouldGetLong() {
    Config config =
        Config.builder().addProperty("prop.long", "12").addProperty("prop.wrong", "twelve").build();

    assertEquals(12, config.getLong("prop.long", 1000));
    assertEquals(1000, config.getLong("prop.wrong", 1000));
    assertEquals(1000, config.getLong("prop.missing", 1000));
  }

  @Test
  void shouldGetDouble() {
    Config config =
        Config.builder()
            .addProperty("prop.double", "12.345")
            .addProperty("prop.wrong", "twelve point something")
            .build();

    assertEquals(12.345, config.getDouble("prop.double", 99.99));
    assertEquals(99.99, config.getDouble("prop.wrong", 99.99));
    assertEquals(99.99, config.getDouble("prop.missing", 99.99));
  }

  @Test
  void shouldGetDuration_defaultUnit() {
    Config config =
        Config.builder()
            .addProperty("prop.duration", "5000")
            .addProperty("prop.wrong", "hundred days")
            .build();

    assertEquals(Duration.ofMillis(5000), config.getDuration("prop.duration", Duration.ZERO));
    assertEquals(Duration.ZERO, config.getDuration("prop.wrong", Duration.ZERO));
    assertEquals(Duration.ZERO, config.getDuration("prop.missing", Duration.ZERO));
  }

  @Test
  void shouldGetDuration_variousUnits() {
    Config config = Config.builder().addProperty("prop.duration", "100ms").build();
    assertEquals(Duration.ofMillis(100), config.getDuration("prop.duration", Duration.ZERO));

    config = Config.builder().addProperty("prop.duration", "100s").build();
    assertEquals(Duration.ofSeconds(100), config.getDuration("prop.duration", Duration.ZERO));

    config = Config.builder().addProperty("prop.duration", "100m").build();
    assertEquals(Duration.ofMinutes(100), config.getDuration("prop.duration", Duration.ZERO));

    config = Config.builder().addProperty("prop.duration", "100h").build();
    assertEquals(Duration.ofHours(100), config.getDuration("prop.duration", Duration.ZERO));

    config = Config.builder().addProperty("prop.duration", "100d").build();
    assertEquals(Duration.ofDays(100), config.getDuration("prop.duration", Duration.ZERO));
  }

  @Test
  void shouldGetList() {
    Config config = Config.builder().addProperty("prop.list", "one, two ,three").build();

    assertEquals(
        asList("one", "two", "three"), config.getList("prop.list", singletonList("default")));
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

    assertThat(config.getMap("prop.map", singletonMap("three", "3")))
        .containsOnly(entry("one", "1"), entry("two", "2"));
    assertThat(config.getMap("prop.wrong", singletonMap("three", "3")))
        .containsOnly(entry("three", "3"));
    assertThat(config.getMap("prop.missing", singletonMap("three", "3")))
        .containsOnly(entry("three", "3"));
    assertThat(config.getMap("prop.trailing", emptyMap())).containsOnly(entry("one", "1"));
  }
}
