/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.OpenTelemetryConfigurationModelAccessor.getInstrumentation;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpClientInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpInstrumentationModel;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            getInstrumentation(model)
                .getJava()
                .getAdditionalProperties()
                .get("micrometer")
                .getAdditionalProperties())
        .containsEntry("base_time_unit", "s");
    assertThat(
            getInstrumentation(model)
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

    DeclarativeConfigProperties instrumentation = config.get("java").get("example_instrumentation");
    assertThat(instrumentation.getBoolean("bool_key")).isTrue();
    assertThat(instrumentation.getLong("int_key")).isEqualTo(42L);
    assertThat(instrumentation.getDouble("double_key")).isEqualTo(3.14);
  }

  @Test
  void applyToModelPreservesTypedListDefaults() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").get("http").setDefault("known_methods", asList("GET", "POST"));

    OpenTelemetryConfigurationModel model = newModel();
    defaults.applyToModel(model);

    DeclarativeConfigProperties config =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
            .getInstrumentationConfig();
    assertThat(
            config
                .get("java")
                .get("common")
                .get("http")
                .getScalarList("known_methods", String.class))
        .containsExactly("GET", "POST");
  }

  @Test
  void applyToModelCopiesListDefaults() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").get("http").setDefault("known_methods", asList("GET", "POST"));
    Map<String, String> servicePeerMapping = new HashMap<>();
    servicePeerMapping.put("peer", "example.com");
    servicePeerMapping.put("service_name", "checkout");
    defaults.get("common").setDefault("service_peer_mapping", singletonList(servicePeerMapping));

    OpenTelemetryConfigurationModel firstModel = newModel();
    DefaultInstrumentationConfig seed = new DefaultInstrumentationConfig();
    seed.get("seed").setDefault("enabled", true);
    seed.applyToModel(firstModel);
    defaults.applyToModel(firstModel);
    Map<String, Object> firstCommon =
        getInstrumentation(firstModel)
            .getJava()
            .getAdditionalProperties()
            .get("common")
            .getAdditionalProperties();
    Object firstHttpValue = firstCommon.get("http");
    @SuppressWarnings("unchecked")
    Map<String, Object> firstHttp = (Map<String, Object>) firstHttpValue;
    ((List<?>) firstHttp.get("known_methods")).clear();
    Object firstMappingsValue = firstCommon.get("service_peer_mapping");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> firstMappings = (List<Map<String, Object>>) firstMappingsValue;
    firstMappings.get(0).put("service_name", "mutated");

    OpenTelemetryConfigurationModel secondModel = newModel();
    defaults.applyToModel(secondModel);
    DeclarativeConfigProperties secondConfig =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(secondModel))
            .getInstrumentationConfig();

    assertThat(
            secondConfig
                .get("java")
                .get("common")
                .get("http")
                .getScalarList("known_methods", String.class))
        .containsExactly("GET", "POST");
    List<DeclarativeConfigProperties> secondMappings =
        secondConfig.get("java").get("common").getStructuredList("service_peer_mapping");
    assertThat(secondMappings).hasSize(1);
    assertThat(secondMappings.get(0).getString("service_name")).isEqualTo("checkout");
    assertThat(defaults.toConfigProperties())
        .containsEntry("otel.instrumentation.http.known-methods", "GET,POST")
        .containsEntry("otel.instrumentation.common.peer-service-mapping", "example.com=checkout");
  }

  @Test
  void applyToModelPreservesExistingListDefaults() {
    DefaultInstrumentationConfig seed = new DefaultInstrumentationConfig();
    seed.get("common").get("http").setDefault("known_methods", singletonList("GET"));

    OpenTelemetryConfigurationModel model = newModel();
    seed.applyToModel(model);
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").get("http").setDefault("known_methods", singletonList("POST"));
    defaults.applyToModel(model);

    DeclarativeConfigProperties config =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
            .getInstrumentationConfig();
    assertThat(
            config
                .get("java")
                .get("common")
                .get("http")
                .getScalarList("known_methods", String.class))
        .containsExactly("GET");
  }

  @Test
  void applyToModelAppliesGeneralDefaults() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    setGeneralClientRequestHeaders(defaults, "X-Request-Id");

    OpenTelemetryConfigurationModel model = newModel();
    defaults.applyToModel(model);

    assertThat(
            getInstrumentation(model)
                .getGeneral()
                .getHttp()
                .getClient()
                .getRequestCapturedHeaders())
        .containsExactly("X-Request-Id");
    assertThat(getInstrumentation(model).getJava()).isNull();
  }

  @Test
  void applyToModelDoesNotOverrideExistingGeneralDefault() {
    OpenTelemetryConfigurationModel model = newModel();
    DefaultInstrumentationConfig seed = new DefaultInstrumentationConfig();
    setGeneralClientRequestHeaders(seed, "Existing");
    seed.applyToModel(model);

    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    setGeneralClientRequestHeaders(defaults, "Default");
    defaults.customizeGeneral(
        general ->
            general.getHttp().getClient().setResponseCapturedHeaders(asList("Default-Response")));
    defaults.applyToModel(model);

    assertThat(
            getInstrumentation(model)
                .getGeneral()
                .getHttp()
                .getClient()
                .getRequestCapturedHeaders())
        .containsExactly("Existing");
    assertThat(
            getInstrumentation(model)
                .getGeneral()
                .getHttp()
                .getClient()
                .getResponseCapturedHeaders())
        .containsExactly("Default-Response");
  }

  @Test
  void applyToModelTreatsGeneralNamedJavaInstrumentationAsJava() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("general").setDefault("enabled", true);

    OpenTelemetryConfigurationModel model = newModel();
    defaults.applyToModel(model);

    assertThat(getInstrumentation(model).getGeneral()).isNull();
    assertThat(
            getInstrumentation(model)
                .getJava()
                .getAdditionalProperties()
                .get("general")
                .getAdditionalProperties())
        .containsEntry("enabled", true);
  }

  @Test
  void applyToModelSupportsNestedPaths() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    OpenTelemetryConfigurationModel model = newModel();
    DefaultInstrumentationConfigApplier.applyToModel(defaults, model);

    assertThat(
            getInstrumentation(model)
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
            getInstrumentation(model)
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
            getInstrumentation(model)
                .getJava()
                .getAdditionalProperties()
                .get("acme")
                .getAdditionalProperties())
        .containsEntry("full_name", singletonMap("preserved", "true"));
  }

  private static void setGeneralClientRequestHeaders(
      DefaultInstrumentationConfig defaults, String header) {
    defaults.customizeGeneral(
        general ->
            general.setHttp(
                new ExperimentalHttpInstrumentationModel()
                    .setClient(
                        new ExperimentalHttpClientInstrumentationModel()
                            .setRequestCapturedHeaders(singletonList(header)))));
  }
}
