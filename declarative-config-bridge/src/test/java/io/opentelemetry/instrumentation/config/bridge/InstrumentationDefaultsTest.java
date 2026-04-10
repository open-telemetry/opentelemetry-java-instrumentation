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
    defaults.setDefault("micrometer", "base_time_unit", "s");
    defaults.setDefault("log4j_appender", "experimental_log_attributes", "true");

    Map<String, String> props = defaults.toConfigProperties();

    assertThat(props)
        .containsEntry("otel.instrumentation.micrometer.base-time-unit", "s")
        .containsEntry("otel.instrumentation.log4j-appender.experimental-log-attributes", "true")
        .hasSize(2);
  }

  @Test
  void applyToModel() {
    InstrumentationDefaults defaults = new InstrumentationDefaults();
    defaults.setDefault("micrometer", "base_time_unit", "s");

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
  }

  @Test
  void applyToModelDoesNotOverrideExisting() {
    InstrumentationDefaults defaults = new InstrumentationDefaults();
    defaults.setDefault("micrometer", "base_time_unit", "s");

    // Pre-populate model with a different value
    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    new InstrumentationDefaults()
        .setDefault("micrometer", "base_time_unit", "ms")
        .applyToModel(model);

    // Apply defaults — should not override
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
