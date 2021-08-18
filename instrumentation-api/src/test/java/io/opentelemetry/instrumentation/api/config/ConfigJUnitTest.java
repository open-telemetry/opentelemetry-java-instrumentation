/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// suppress duration unit check, e.g. ofMillis(5000) -> ofSeconds(5)
@SuppressWarnings("CanonicalDuration")
class ConfigJUnitTest {
  @Test
  void shouldGetString() {
    Config config = Config.newBuilder().addProperty("prop.string", "some text").build();

    assertEquals("some text", config.getProperty("prop.string"));
    assertEquals("some text", config.getProperty("prop.string", "default"));
    assertNull(config.getProperty("prop.missing"));
    assertEquals("default", config.getProperty("prop.missing", "default"));
  }

  @Test
  void shouldGetBoolean() {
    Config config = Config.newBuilder().addProperty("prop.boolean", "true").build();

    assertTrue(config.getBoolean("prop.boolean"));
    assertTrue(config.getBooleanProperty("prop.boolean", false));
    assertNull(config.getBoolean("prop.missing"));
    assertFalse(config.getBooleanProperty("prop.missing", false));
  }

  @Test
  void shouldGetInt() {
    Config config =
        Config.newBuilder()
            .addProperty("prop.int", "12")
            .addProperty("prop.wrong", "twelve")
            .build();

    assertEquals(12, config.getInt("prop.int"));
    assertEquals(12, config.getInt("prop.int", 1000));
    assertNull(config.getInt("prop.wrong"));
    assertEquals(1000, config.getInt("prop.wrong", 1000));
    assertNull(config.getInt("prop.missing"));
    assertEquals(1000, config.getInt("prop.missing", 1000));
  }

  @Test
  void shouldGetLong() {
    Config config =
        Config.newBuilder()
            .addProperty("prop.long", "12")
            .addProperty("prop.wrong", "twelve")
            .build();

    assertEquals(12, config.getLong("prop.long"));
    assertEquals(12, config.getLong("prop.long", 1000));
    assertNull(config.getLong("prop.wrong"));
    assertEquals(1000, config.getLong("prop.wrong", 1000));
    assertNull(config.getLong("prop.missing"));
    assertEquals(1000, config.getLong("prop.missing", 1000));
  }

  @Test
  void shouldGetDouble() {
    Config config =
        Config.newBuilder()
            .addProperty("prop.double", "12.345")
            .addProperty("prop.wrong", "twelve point something")
            .build();

    assertEquals(12.345, config.getDouble("prop.double"));
    assertEquals(12.345, config.getDouble("prop.double", 99.99));
    assertNull(config.getDouble("prop.wrong"));
    assertEquals(99.99, config.getDouble("prop.wrong", 99.99));
    assertNull(config.getDouble("prop.missing"));
    assertEquals(99.99, config.getDouble("prop.missing", 99.99));
  }

  @Test
  void shouldGetDuration_defaultUnit() {
    Config config =
        Config.newBuilder()
            .addProperty("prop.duration", "5000")
            .addProperty("prop.wrong", "hundred days")
            .build();

    assertEquals(Duration.ofMillis(5000), config.getDuration("prop.duration"));
    assertEquals(Duration.ofMillis(5000), config.getDuration("prop.duration", Duration.ZERO));
    assertNull(config.getDuration("prop.wrong"));
    assertEquals(Duration.ZERO, config.getDuration("prop.wrong", Duration.ZERO));
    assertNull(config.getDuration("prop.missing"));
    assertEquals(Duration.ZERO, config.getDuration("prop.missing", Duration.ZERO));
  }

  @Test
  void shouldGetDuration_variousUnits() {
    Config config = Config.newBuilder().addProperty("prop.duration", "100ms").build();
    assertEquals(Duration.ofMillis(100), config.getDuration("prop.duration"));

    config = Config.newBuilder().addProperty("prop.duration", "100s").build();
    assertEquals(Duration.ofSeconds(100), config.getDuration("prop.duration"));

    config = Config.newBuilder().addProperty("prop.duration", "100m").build();
    assertEquals(Duration.ofMinutes(100), config.getDuration("prop.duration"));

    config = Config.newBuilder().addProperty("prop.duration", "100h").build();
    assertEquals(Duration.ofHours(100), config.getDuration("prop.duration"));

    config = Config.newBuilder().addProperty("prop.duration", "100d").build();
    assertEquals(Duration.ofDays(100), config.getDuration("prop.duration"));
  }

  @Test
  void shouldGetList() {
    Config config = Config.newBuilder().addProperty("prop.list", "one, two ,three").build();

    assertEquals(asList("one", "two", "three"), config.getListProperty("prop.list"));
    assertEquals(
        asList("one", "two", "three"),
        config.getListProperty("prop.list", singletonList("default")));
    assertTrue(config.getListProperty("prop.missing").isEmpty());
    assertEquals(
        singletonList("default"), config.getListProperty("prop.missing", singletonList("default")));
  }

  @Test
  void shouldGetMap() {
    Config config =
        Config.newBuilder()
            .addProperty("prop.map", "one=1, two=2")
            .addProperty("prop.wrong", "one=1, but not two!")
            .build();

    assertEquals(map("one", "1", "two", "2"), config.getMapProperty("prop.map"));
    assertEquals(
        map("one", "1", "two", "2"), config.getMapProperty("prop.map", singletonMap("three", "3")));
    assertTrue(config.getMapProperty("prop.wrong").isEmpty());
    assertEquals(
        singletonMap("three", "3"),
        config.getMapProperty("prop.wrong", singletonMap("three", "3")));
    assertTrue(config.getMapProperty("prop.missing").isEmpty());
    assertEquals(
        singletonMap("three", "3"),
        config.getMapProperty("prop.missing", singletonMap("three", "3")));
  }

  public static Map<String, String> map(String k1, String v1, String k2, String v2) {
    Map<String, String> map = new HashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }
}
