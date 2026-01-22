/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

@SuppressWarnings("deprecation") // using deprecated config property
class ConfigPropertiesUtilTest {

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "false")
  @SetSystemProperty(key = "test.property.boolean", value = "true")
  @Test
  void getBoolean_systemProperty() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isTrue();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "true")
  @Test
  void getBoolean_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isTrue();
  }

  @Test
  void getBoolean_none() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isFalse();
  }
}
