/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;

class ConfigPropertiesDeclarativeConfigPropertiesTest {

  @Test
  void shouldBridgeConfigProperties() {
    ConfigProperties configProperties = mock(ConfigProperties.class);
    when(configProperties.getString("otel.instrumentation.foo.bar")).thenReturn("baz");
    when(configProperties.getString("otel.instrumentation.experimental.foo.bar")).thenReturn("qux");

    ConfigProvider configProvider = ConfigPropertiesDeclarativeConfigProperties.create(configProperties);
    DeclarativeConfigProperties properties = configProvider.getInstrumentationConfig();

    assertThat(properties.getStructured("java").getStructured("foo").getString("bar")).isEqualTo("baz");
    assertThat(properties.getStructured("java").getStructured("foo/development").getString("bar")).isEqualTo("qux");
  }
}

