/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.declarativeconfig.internal.model.OpenTelemetryConfigurationModel;
import org.junit.jupiter.api.Test;

class DefaultInstrumentationConfigApplierTest {

  @Test
  void applyToModel() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    defaults.get("log4j_appender").setDefault("experimental_log_attributes/development", "true");

    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    DefaultInstrumentationConfigApplier.applyToModel(defaults, model);

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
  void applyToModelSupportsNestedPaths() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    DefaultInstrumentationConfigApplier.applyToModel(defaults, model);

    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("acme")
                .getAdditionalProperties())
        .containsEntry("full_name", singletonMap("preserved", "true"));
  }

  @Test
  void applyToModelDoesNotOverrideExisting() {
    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    DefaultInstrumentationConfig seed = new DefaultInstrumentationConfig();
    seed.get("micrometer").setDefault("base_time_unit", "ms");
    DefaultInstrumentationConfigApplier.applyToModel(seed, model);

    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    DefaultInstrumentationConfigApplier.applyToModel(defaults, model);

    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("micrometer")
                .getAdditionalProperties())
        .containsEntry("base_time_unit", "ms");
  }

  @Test
  void applyToModelDoesNotOverrideExistingNestedValues() {
    OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();
    DefaultInstrumentationConfig seed = new DefaultInstrumentationConfig();
    seed.get("acme").get("full_name").setDefault("preserved", "true");
    DefaultInstrumentationConfigApplier.applyToModel(seed, model);

    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("acme").get("full_name").setDefault("preserved", "false");
    DefaultInstrumentationConfigApplier.applyToModel(defaults, model);

    assertThat(
            model
                .getInstrumentationDevelopment()
                .getJava()
                .getAdditionalProperties()
                .get("acme")
                .getAdditionalProperties())
        .containsEntry("full_name", singletonMap("preserved", "true"));
  }
}
