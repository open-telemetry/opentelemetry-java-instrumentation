/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentConfigTest {

  @ParameterizedTest(name = "isInstrumentationEnabled({0}) = {4}")
  @MethodSource("instrumentationEnabledParams")
  void testIsInstrumentationEnabled(
      @SuppressWarnings("unused") String description,
      Boolean firstEnabled,
      Boolean secondEnabled,
      boolean defaultEnabled,
      boolean expected) {

    Map<String, String> config = new HashMap<>();
    if (firstEnabled != null) {
      config.put("otel.instrumentation.first.enabled", firstEnabled.toString());
    }
    if (secondEnabled != null) {
      config.put("otel.instrumentation.second.enabled", secondEnabled.toString());
    }

    // Reset GlobalOpenTelemetry and configure with test properties
    GlobalOpenTelemetry.resetForTest();
    OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> config)
            .setResultAsGlobal()
            .build()
            .getOpenTelemetrySdk();

    try {
      assertEquals(
          expected,
          AgentConfig.isInstrumentationEnabled(
              new TreeSet<>(asList("first", "second")), defaultEnabled));
    } finally {
      sdk.close();
      GlobalOpenTelemetry.resetForTest();
    }
  }

  private static Stream<Arguments> instrumentationEnabledParams() {
    return Stream.of(
        Arguments.of(
            "enabled by default, both instrumentations are off", false, false, true, false),
        Arguments.of("enabled by default, first instrumentation is on", true, null, true, true),
        Arguments.of("enabled by default, second instrumentation is on", null, true, true, true),
        Arguments.of("enabled by default, both instrumentations are on", true, true, true, true),
        Arguments.of(
            "enabled by default, first instrumentation is off, second is on",
            false,
            true,
            true,
            false),
        Arguments.of(
            "enabled by default, first instrumentation is on, second is off",
            true,
            false,
            true,
            true),
        Arguments.of("enabled by default", null, null, true, true),
        Arguments.of(
            "disabled by default, both instrumentations are off", false, false, false, false),
        Arguments.of("disabled by default, first instrumentation is on", true, null, false, true),
        Arguments.of("disabled by default, second instrumentation is on", null, true, false, true),
        Arguments.of("disabled by default, both instrumentation are on", true, true, false, true),
        Arguments.of(
            "disabled by default, first instrumentation is off, second is on",
            false,
            true,
            false,
            false),
        Arguments.of(
            "disabled by default, first instrumentation is on, second is off",
            true,
            false,
            false,
            true),
        Arguments.of("disabled by default", null, null, false, false));
  }
}
