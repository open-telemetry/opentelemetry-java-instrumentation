/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class DefaultInstrumentationConfigApplierTest {

  private static OpenTelemetryConfigurationModel newModel() {
    return DeclarativeConfiguration.parse(
        new ByteArrayInputStream("file_format: \"1.0\"\n".getBytes(UTF_8)));
  }

  @Test
  void applyToModel() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("micrometer").setDefault("base_time_unit", "s");
    defaults.get("log4j_appender").setDefault("experimental_log_attributes/development", true);

    OpenTelemetryConfigurationModel model = newModel();
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
        .containsEntry("experimental_log_attributes/development", true);
  }

  @Test
  void applyToModelPreservesTypedScalarDefaults() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("example_instrumentation").setDefault("bool_key", true);
    defaults.get("example_instrumentation").setDefault("int_key", 42L);
    defaults.get("example_instrumentation").setDefault("double_key", 3.14);

    OpenTelemetryConfigurationModel model = newModel();
    defaults.applyToModel(model);

    DeclarativeConfigProperties config =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
            .getInstrumentationConfig();

    DeclarativeConfigProperties instrumentation =
        config.getStructured("java").getStructured("example_instrumentation");
    assertThat(instrumentation.getBoolean("bool_key")).isTrue();
    assertThat(instrumentation.getLong("int_key")).isEqualTo(42L);
    assertThat(instrumentation.getDouble("double_key")).isEqualTo(3.14);
  }

  @Test
  void applyToModelSupportsNestedPaths() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    OpenTelemetryConfigurationModel model = newModel();
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
    OpenTelemetryConfigurationModel model = newModel();
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
    OpenTelemetryConfigurationModel model = newModel();
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
