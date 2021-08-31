/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.sdk.autoconfigure.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.ConfigurationException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Copied from
// https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/src/test/java/io/opentelemetry/sdk/autoconfigure/ConfigPropertiesTest.java
class ConfigPropertiesAdapterTest {

  @Test
  void allValid() {
    Map<String, String> properties = new HashMap<>();
    properties.put("string", "str");
    properties.put("int", "10");
    properties.put("long", "20");
    properties.put("double", "5.4");
    properties.put("list", "cat,dog,bear");
    properties.put("map", "cat=meow,dog=bark,bear=growl");
    properties.put("duration", "1s");

    ConfigProperties config = createConfig(properties);
    assertThat(config.getString("string")).isEqualTo("str");
    assertThat(config.getInt("int")).isEqualTo(10);
    assertThat(config.getLong("long")).isEqualTo(20);
    assertThat(config.getDouble("double")).isEqualTo(5.4);
    assertThat(config.getCommaSeparatedValues("list")).containsExactly("cat", "dog", "bear");
    assertThat(config.getCommaSeparatedMap("map"))
        .containsExactly(entry("cat", "meow"), entry("dog", "bark"), entry("bear", "growl"));
    assertThat(config.getDuration("duration")).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void allMissing() {
    ConfigProperties config = createConfig(Collections.emptyMap());
    assertThat(config.getString("string")).isNull();
    assertThat(config.getInt("int")).isNull();
    assertThat(config.getLong("long")).isNull();
    assertThat(config.getDouble("double")).isNull();
    assertThat(config.getCommaSeparatedValues("list")).isEmpty();
    assertThat(config.getCommaSeparatedMap("map")).isEmpty();
    assertThat(config.getDuration("duration")).isNull();
  }

  @Test
  void allEmpty() {
    Map<String, String> properties = new HashMap<>();
    properties.put("string", "");
    properties.put("int", "");
    properties.put("long", "");
    properties.put("double", "");
    properties.put("list", "");
    properties.put("map", "");
    properties.put("duration", "");

    ConfigProperties config = createConfig(properties);
    assertThat(config.getString("string")).isEmpty();
    assertThat(config.getInt("int")).isNull();
    assertThat(config.getLong("long")).isNull();
    assertThat(config.getDouble("double")).isNull();
    assertThat(config.getCommaSeparatedValues("list")).isEmpty();
    assertThat(config.getCommaSeparatedMap("map")).isEmpty();
    assertThat(config.getDuration("duration")).isNull();
  }

  @Test
  void invalidInt() {
    assertThatThrownBy(() -> createConfig(Collections.singletonMap("int", "bar")).getInt("int"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property int=bar. Must be a integer.");
    assertThatThrownBy(
            () -> createConfig(Collections.singletonMap("int", "999999999999999")).getInt("int"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property int=999999999999999. Must be a integer.");
  }

  @Test
  void invalidLong() {
    assertThatThrownBy(() -> createConfig(Collections.singletonMap("long", "bar")).getLong("long"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property long=bar. Must be a long.");
    assertThatThrownBy(
            () ->
                createConfig(Collections.singletonMap("long", "99223372036854775807"))
                    .getLong("long"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property long=99223372036854775807. Must be a long.");
  }

  @Test
  void invalidDouble() {
    assertThatThrownBy(
            () -> createConfig(Collections.singletonMap("double", "bar")).getDouble("double"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property double=bar. Must be a double.");
    assertThatThrownBy(
            () -> createConfig(Collections.singletonMap("double", "1.0.1")).getDouble("double"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid value for property double=1.0.1. Must be a double.");
  }

  @Test
  void uncleanList() {
    assertThat(
            createConfig(Collections.singletonMap("list", "  a  ,b,c  ,  d,,   ,"))
                .getCommaSeparatedValues("list"))
        .containsExactly("a", "b", "c", "d");
  }

  @Test
  void uncleanMap() {
    assertThat(
            createConfig(Collections.singletonMap("map", "  a=1  ,b=2,c = 3  ,  d=  4,,  ,"))
                .getCommaSeparatedMap("map"))
        .containsExactly(entry("a", "1"), entry("b", "2"), entry("c", "3"), entry("d", "4"));
  }

  @Test
  void invalidMap() {
    assertThatThrownBy(
            () ->
                createConfig(Collections.singletonMap("map", "a=1,b=")).getCommaSeparatedMap("map"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid map property: map=a=1,b=");
    assertThatThrownBy(
            () ->
                createConfig(Collections.singletonMap("map", "a=1,b")).getCommaSeparatedMap("map"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid map property: map=a=1,b");
    assertThatThrownBy(
            () ->
                createConfig(Collections.singletonMap("map", "a=1,=b")).getCommaSeparatedMap("map"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid map property: map=a=1,=b");
  }

  @Test
  void invalidDuration() {
    assertThatThrownBy(
            () ->
                createConfig(Collections.singletonMap("duration", "1a1ms")).getDuration("duration"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid duration property duration=1a1ms. Expected number, found: 1a1");
    assertThatThrownBy(
            () -> createConfig(Collections.singletonMap("duration", "9mm")).getDuration("duration"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Invalid duration property duration=9mm. Invalid duration string, found: mm");
  }

  @Test
  void durationUnitParsing() {
    assertThat(createConfig(Collections.singletonMap("duration", "1")).getDuration("duration"))
        .isEqualTo(Duration.ofMillis(1));
    assertThat(createConfig(Collections.singletonMap("duration", "2ms")).getDuration("duration"))
        .isEqualTo(Duration.ofMillis(2));
    assertThat(createConfig(Collections.singletonMap("duration", "3s")).getDuration("duration"))
        .isEqualTo(Duration.ofSeconds(3));
    assertThat(createConfig(Collections.singletonMap("duration", "4m")).getDuration("duration"))
        .isEqualTo(Duration.ofMinutes(4));
    assertThat(createConfig(Collections.singletonMap("duration", "5h")).getDuration("duration"))
        .isEqualTo(Duration.ofHours(5));
    assertThat(createConfig(Collections.singletonMap("duration", "6d")).getDuration("duration"))
        .isEqualTo(Duration.ofDays(6));
    // Check Space handling
    assertThat(createConfig(Collections.singletonMap("duration", "7 ms")).getDuration("duration"))
        .isEqualTo(Duration.ofMillis(7));
    assertThat(createConfig(Collections.singletonMap("duration", "8   ms")).getDuration("duration"))
        .isEqualTo(Duration.ofMillis(8));
  }

  private static ConfigProperties createConfig(Map<String, String> properties) {
    return new ConfigPropertiesAdapter(Config.newBuilder().readProperties(properties).build());
  }
}
