/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewGenAiSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

class SemconvStabilityTest {

  @Test
  @ClearSystemProperty(key = "otel.semconv-stability.preview")
  void genAiPreviewIsOffByDefault() {
    assertThat(emitPreviewGenAiSemconv(OpenTelemetry.noop())).isFalse();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"gen_ai | true", "' gen_ai , rpc ' | true", "rpc | false", "'' | false"})
  @ClearSystemProperty(key = "otel.semconv-stability.preview")
  void genAiPreviewFromSystemProperty(String preview, boolean expected) {
    System.setProperty("otel.semconv-stability.preview", preview);
    assertThat(emitPreviewGenAiSemconv(OpenTelemetry.noop())).isEqualTo(expected);
  }

  @Test
  @ClearSystemProperty(key = "otel.semconv-stability.preview")
  void genAiPreviewFromDeclarativeConfig() {
    assertThat(emitPreviewGenAiSemconv(declarative("[gen_ai]"))).isTrue();
  }

  @Test
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "gen_ai")
  void emptyDeclarativePreviewDisablesSystemPropertyFallback() {
    assertThat(emitPreviewGenAiSemconv(declarative("[]"))).isFalse();
  }

  private static OpenTelemetry declarative(String previewList) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      semconv_stability:\n"
            + "        preview: "
            + previewList
            + "\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    ConfigProvider configProvider =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model));
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(configProvider.getInstrumentationConfig("common"));
    return openTelemetry;
  }
}
