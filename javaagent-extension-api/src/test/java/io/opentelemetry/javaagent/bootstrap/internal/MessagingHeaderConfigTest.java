/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MessagingHeaderConfigTest {

  @ParameterizedTest
  @MethodSource("yamlSelectors")
  void resolvesYamlSelectorsPerLeaf(
      String common, String older, List<String> included, List<String> excluded) {
    IncludeExclude headers =
        new ExperimentalConfig(yamlOpenTelemetry(common, older, false)).getMessagingHeaders();

    assertThat(headers.getIncluded()).isEqualTo(included);
    assertThat(headers.getExcluded()).isEqualTo(excluded);
  }

  private static Stream<Arguments> yamlSelectors() {
    return Stream.of(
        argumentSet(
            "published common selector",
            "headers/development:\n  included: [Test-*]\n  excluded: [Test-secret]",
            "",
            singletonList("Test-*"),
            singletonList("Test-secret")),
        argumentSet(
            "stable included and common excluded",
            "headers:\n  included: [stable-*]\n"
                + "headers/development:\n  included: [common-*]\n  excluded: [secret]",
            "headers/development:\n  included: [older-*]\n  excluded: [older-secret]",
            singletonList("stable-*"),
            singletonList("secret")),
        argumentSet(
            "common included and stable excluded",
            "headers:\n  excluded: [stable-secret]\n"
                + "headers/development:\n  included: [common-*]\n  excluded: [secret]",
            "headers/development:\n  included: [older-*]\n  excluded: [older-secret]",
            singletonList("common-*"),
            singletonList("stable-secret")),
        argumentSet(
            "common included and older excluded",
            "headers/development:\n  included: [common-*]",
            "headers/development:\n  included: [older-*]\n  excluded: [secret]",
            singletonList("common-*"),
            singletonList("secret")),
        argumentSet(
            "older included and common excluded",
            "headers/development:\n  excluded: [secret]",
            "headers/development:\n  included: [older-*]\n  excluded: [older-secret]",
            singletonList("older-*"),
            singletonList("secret")),
        argumentSet(
            "explicit empty stable included overrides both aliases",
            "headers:\n  included: []\n"
                + "headers/development:\n  included: [common-*]\n  excluded: [secret]",
            "headers/development:\n  included: [older-*]",
            emptyList(),
            singletonList("secret")),
        argumentSet(
            "empty stable leaves override aliases and capture fallback",
            "headers:\n  included: []\n  excluded: []\n"
                + "headers/development:\n  included: [common-*]\n"
                + "capture_headers/development: [capture]",
            "headers/development:\n  included: [older-*]\n  excluded: [secret]",
            emptyList(),
            emptyList()),
        argumentSet(
            "empty selectors preserve capture fallback",
            "headers: {}\nheaders/development: {}\ncapture_headers/development: [capture]",
            "",
            singletonList("capture"),
            emptyList()),
        argumentSet(
            "empty deprecated common leaves preserve capture fallback",
            "headers/development:\n  included: []\n  excluded: []\n"
                + "capture_headers/development: [capture]",
            "",
            singletonList("capture"),
            emptyList()),
        argumentSet("no selector captures nothing", "", "", emptyList(), emptyList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void v3PreviewIgnoresDeprecatedYamlSelectors(boolean stable) {
    ExtendedOpenTelemetry openTelemetry =
        yamlOpenTelemetry(
            (stable ? "headers:\n  included: [Test-*]\n  excluded: [Test-secret]\n" : "")
                + "headers/development:\n  included: [common-*]\n  excluded: [Test-public]\n"
                + "capture_headers/development: [capture]",
            "headers/development:\n  included: [older-*]\n  excluded: [Test-public]",
            true);

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.isEmpty()).isEqualTo(!stable);
    assertThat(headers.getIncluded()).isEqualTo(stable ? singletonList("Test-*") : emptyList());
    assertThat(headers.getExcluded())
        .isEqualTo(stable ? singletonList("Test-secret") : emptyList());
    if (stable) {
      assertThat(headers.matches("Test-public")).isTrue();
      assertThat(headers.matches("test-public")).isFalse();
      assertThat(headers.matches("Test-secret")).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void resolvesFlatAliasesThroughBridge(boolean preview) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.instrumentation.common.v3-preview", Boolean.toString(preview));
    properties.put("otel.instrumentation.common.messaging.headers.included", "Test-*");
    properties.put(
        "otel.instrumentation.common.messaging.experimental.headers.included", "common-*");
    properties.put(
        "otel.instrumentation.common.messaging.experimental.headers.excluded", "Test-secret");
    properties.put("otel.instrumentation.messaging.experimental.headers.included", "older-*");
    properties.put("otel.instrumentation.messaging.experimental.headers.excluded", "Test-public");
    properties.put("otel.instrumentation.messaging.experimental.capture-headers", "capture");
    DeclarativeConfigProperties javaConfig =
        DeclarativeConfigBridge.createInstrumentationConfig(
                DefaultConfigProperties.createFromMap(properties))
            .getInstrumentationConfig()
            .get("java");

    IncludeExclude headers =
        new ExperimentalConfig(openTelemetry(javaConfig)).getMessagingHeaders();

    assertThat(headers.matches("Test-public")).isTrue();
    assertThat(headers.matches("test-public")).isFalse();
    assertThat(headers.matches("Test-secret")).isEqualTo(preview);
    assertThat(headers.getIncluded()).containsExactly("Test-*");
    assertThat(headers.getExcluded())
        .isEqualTo(preview ? emptyList() : singletonList("Test-secret"));
  }

  private static ExtendedOpenTelemetry yamlOpenTelemetry(
      String common, String older, boolean preview) {
    String yaml =
        "file_format: '1.0'\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      v3_preview: "
            + preview
            + "\n"
            + "      messaging:\n"
            + "        "
            + common.replace("\n", "\n        ")
            + "\n"
            + "    messaging:\n"
            + "      "
            + older.replace("\n", "\n      ")
            + "\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    return openTelemetry(
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
            .getInstrumentationConfig()
            .get("java"));
  }

  private static ExtendedOpenTelemetry openTelemetry(DeclarativeConfigProperties javaConfig) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getInstrumentationConfig(anyString()))
        .thenAnswer(invocation -> javaConfig.get(invocation.getArgument(0)));
    return openTelemetry;
  }
}
