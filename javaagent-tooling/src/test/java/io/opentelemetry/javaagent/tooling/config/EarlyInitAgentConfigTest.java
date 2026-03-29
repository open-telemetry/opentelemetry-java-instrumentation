/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

class EarlyInitAgentConfigTest {
  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @SetSystemProperty(key = "test.property.string", value = "sys")
  @Test
  void getString_systemProperty() {
    assertThat(EarlyInitAgentConfig.get().getString("test.property.string")).isEqualTo("sys");
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @Test
  void getString_environmentVariable() {
    assertThat(EarlyInitAgentConfig.get().getString("test.property.string")).isEqualTo("env");
  }

  @Test
  void getString_none() {
    assertThat(EarlyInitAgentConfig.get().getString("test.property.string")).isNull();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "false")
  @SetSystemProperty(key = "test.property.boolean", value = "true")
  @Test
  void getBoolean_systemProperty() {
    assertThat(EarlyInitAgentConfig.get().getBoolean("test.property.boolean", false)).isTrue();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "true")
  @Test
  void getBoolean_environmentVariable() {
    assertThat(EarlyInitAgentConfig.get().getBoolean("test.property.boolean", false)).isTrue();
  }

  @Test
  void getBoolean_none() {
    assertThat(EarlyInitAgentConfig.get().getBoolean("test.property.boolean", false)).isFalse();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "456")
  @SetSystemProperty(key = "test.property.int", value = "123")
  @Test
  void getInt_systemProperty() {
    assertThat(EarlyInitAgentConfig.get().getInt("test.property.int", 0)).isEqualTo(123);
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "456")
  @Test
  void getInt_environmentVariable() {
    assertThat(EarlyInitAgentConfig.get().getInt("test.property.int", 0)).isEqualTo(456);
  }

  @Test
  void getInt_none() {
    assertThat(EarlyInitAgentConfig.get().getInt("test.property.int", 789)).isEqualTo(789);
  }

  @SetSystemProperty(key = "test.property.int", value = "invalid")
  @Test
  void getInt_invalidValue() {
    assertThat(EarlyInitAgentConfig.get().getInt("test.property.int", 999)).isEqualTo(999);
  }
}
