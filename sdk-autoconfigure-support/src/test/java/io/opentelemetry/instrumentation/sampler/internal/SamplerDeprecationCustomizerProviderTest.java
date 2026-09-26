/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SamplerDeprecationCustomizerProviderTest {

  private static final String RULE_BASED_ROUTING =
      "        rule_based_routing:\n"
          + "          fallback_sampler:\n"
          + "            always_on:\n"
          + "          span_kind: SERVER\n"
          + "          rules:\n"
          + "            - action: DROP\n"
          + "              attribute: url.path\n"
          + "              pattern: /actuator.*\n";

  private final TestHandler handler = new TestHandler();

  @BeforeEach
  void attachHandler() {
    Logger.getLogger(LinksBasedSamplerDeprecationCustomizerProvider.class.getName())
        .addHandler(handler);
    Logger.getLogger(RuleBasedRoutingSamplerDeprecationCustomizerProvider.class.getName())
        .addHandler(handler);
  }

  @AfterEach
  void detachHandler() {
    Logger.getLogger(LinksBasedSamplerDeprecationCustomizerProvider.class.getName())
        .removeHandler(handler);
    Logger.getLogger(RuleBasedRoutingSamplerDeprecationCustomizerProvider.class.getName())
        .removeHandler(handler);
  }

  @Test
  void warnsOnlyWhenFlatLinksBasedSamplerIsSelected() {
    Map<String, String> properties = flatProperties("linksbased_parentbased_always_on");
    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .addSamplerCustomizer(
                (sampler, config) -> {
                  assertThat(sampler.getDescription()).contains("LinksBased");
                  return sampler;
                })
            .build()
            .getOpenTelemetrySdk()) {
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getLevel()).isEqualTo(WARNING);
      assertThat(handler.records.get(0).getMessage())
          .contains("linksbased_parentbased_always_on", "there is no replacement");
    }
  }

  @Test
  void doesNotWarnForOtherFlatSamplers() {
    Map<String, String> properties = flatProperties("always_on");
    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .build()
            .getOpenTelemetrySdk()) {
      assertThat(handler.records).isEmpty();
    }
  }

  @Test
  void doesNotWarnWhenSdkIsDisabled() {
    Map<String, String> properties = flatProperties("linksbased_parentbased_always_on");
    properties.put("otel.sdk.disabled", "true");
    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .build()
            .getOpenTelemetrySdk()) {
      assertThat(handler.records).isEmpty();
    }
  }

  @Test
  void rejectsFlatLinksBasedSamplerWhenV3PreviewEnabled() {
    Map<String, String> properties = flatProperties("linksbased_parentbased_always_on");
    properties.put("otel.instrumentation.common.v3-preview", "true");

    assertThatThrownBy(
            () ->
                AutoConfiguredOpenTelemetrySdk.builder()
                    .addPropertiesSupplier(() -> properties)
                    .build())
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("linksbased_parentbased_always_on sampler is not supported");
    assertThat(handler.records).isEmpty();
  }

  @Test
  void warnsOnceForNestedDeclarativeRuleBasedRoutingSampler() {
    String sampler =
        "    parent_based:\n"
            + "      root:\n"
            + RULE_BASED_ROUTING
            + "      remote_parent_sampled:\n"
            + RULE_BASED_ROUTING;

    try (OpenTelemetrySdk sdk = createDeclarativeSdk(sampler)) {
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getLevel()).isEqualTo(WARNING);
      assertThat(handler.records.get(0).getMessage())
          .contains("rule_based_routing", "composite/development rule_based");
    }
  }

  @Test
  void doesNotWarnForOtherDeclarativeSamplers() {
    try (OpenTelemetrySdk sdk = createDeclarativeSdk("    always_on:\n")) {
      assertThat(handler.records).isEmpty();
    }
  }

  @Test
  void rejectsDeclarativeRuleBasedRoutingSamplerWhenV3PreviewEnabled() {
    assertThatThrownBy(() -> createDeclarativeSdk(RULE_BASED_ROUTING, true))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("rule_based_routing sampler is not supported");
    assertThat(handler.records).isEmpty();
  }

  private static OpenTelemetrySdk createDeclarativeSdk(String sampler) {
    return createDeclarativeSdk(sampler, false);
  }

  private static OpenTelemetrySdk createDeclarativeSdk(String sampler, boolean v3Preview) {
    String yaml =
        "file_format: \"1.1\"\n"
            + (v3Preview
                ? "instrumentation/development:\n  java:\n    common:\n      v3_preview: true\n"
                : "")
            + "tracer_provider:\n  sampler:\n"
            + sampler;
    return DeclarativeConfiguration.create(
            DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8))))
        .getSdk();
  }

  private static Map<String, String> flatProperties(String sampler) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.sampler", sampler);
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    return properties;
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
