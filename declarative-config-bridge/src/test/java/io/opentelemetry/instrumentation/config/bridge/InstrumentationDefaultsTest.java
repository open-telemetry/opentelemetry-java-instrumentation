/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstrumentationDefaultsTest {

  @Test
  void toConfigProperties() {
    InstrumentationDefaults defaults = new InstrumentationDefaults();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    defaults
        .get("log4j_appender")
        .setDefault("experimental_log_attributes/development", "true");
    defaults.get("spring_scheduling").setDefault("controller_telemetry/development", "false");
    defaults.get("grpc").setDefault("experimental_span_attributes/development", "true");

    Map<String, String> props = defaults.toConfigProperties();

    assertThat(props)
        .containsEntry("otel.instrumentation.micrometer.base-time-unit", "s")
        .containsEntry("otel.instrumentation.log4j-appender.experimental-log-attributes", "true")
        .containsEntry(
            "otel.instrumentation.spring-scheduling.experimental.controller-telemetry", "false")
        .containsEntry("otel.instrumentation.grpc.experimental-span-attributes", "true")
        .hasSize(4);
  }

  @Test
  void applyToModel() {
    InstrumentationDefaults defaults = new InstrumentationDefaults();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    defaults
        .get("log4j_appender")
        .setDefault("experimental_log_attributes/development", "true");

    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    defaults.applyToModel(model);

    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("micrometer")
                .getAdditionalProperties())
        .containsEntry("base_time_unit", "s");
    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("log4j_appender")
                .getAdditionalProperties())
        .containsEntry("experimental_log_attributes/development", "true");
  }

  @Test
  void applyToModelDoesNotOverrideExisting() {
    // Pre-populate model with a different value
    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    InstrumentationDefaults seed = new InstrumentationDefaults();
    seed.get("micrometer").setDefault("base_time_unit", "ms");
    seed.applyToModel(model);

    // Apply a conflicting default — should not override
    InstrumentationDefaults defaults = new InstrumentationDefaults();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    defaults.applyToModel(model);

    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("micrometer")
                .getAdditionalProperties())
        .containsEntry("base_time_unit", "ms");
  }
}
