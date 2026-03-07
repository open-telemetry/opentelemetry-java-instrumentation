/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigPropertiesBackedConfigProviderTest {
  @Test
  void testBuilderWithMappings() {
    Map<String, String> properties = new HashMap<>();
    properties.put("my.custom.property", "custom-value");

    // ConfigProperties has "my.custom.property"
    // We want to map declarative "my.declarative.prop" to "my.custom.property"

    ConfigProvider provider =
        ConfigPropertiesBackedConfigProvider.builder()
            .addMapping("my.declarative.prop", "my.custom.property")
            .build(DefaultConfigProperties.createFromMap(properties));

    DeclarativeConfigProperties config = provider.getInstrumentationConfig();

    // DeclarativeConfigProperties structure lookup
    // pathWithName("my.declarative.prop") should map to "my.custom.property"
    // However, structure is hierarchical.
    // getStructured("my").getStructured("declarative").getString("prop") ->
    // "my.declarative.prop"

    assertThat(config.getStructured("my").getStructured("declarative").getString("prop"))
        .isEqualTo("custom-value");
  }

  @Test
  void testCreateUsesDefaults() {
    // Verify that create() still supports the default mappings (e.g.
    // otel.instrumentation...)
    // e.g. "java.common.http.known_methods" ->
    // "otel.instrumentation.http.known-methods"

    Map<String, String> properties = new HashMap<>();
    properties.put("otel.instrumentation.http.known-methods", "GET,POST");

    ConfigProvider provider =
        ConfigPropertiesBackedConfigProvider.create(
            DefaultConfigProperties.createFromMap(properties));

    DeclarativeConfigProperties config = provider.getInstrumentationConfig();

    assertThat(
            config
                .getStructured("java")
                .getStructured("common")
                .getStructured("http")
                .getString("known_methods"))
        .isEqualTo("GET,POST");
  }
}
