/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeclarativeConfigDurationUtilTest {

  @Test
  void getDuration_supportsDurationStringsForConfigPropertiesBackedConfig() {
    DeclarativeConfigProperties config =
        DeclarativeConfigBridge.createComponentProperties(
            DefaultConfigProperties.createFromMap(
                singletonMap("otel.inferred.spans.min.duration", "42ms")),
            "otel.inferred.spans.");

    assertThat(DeclarativeConfigDurationUtil.getDuration(config, "min_duration"))
        .isEqualTo(Duration.ofMillis(42));
  }

  @Test
  void getDuration_supportsIntegerMillisForDeclarativeYaml() {
    DeclarativeConfigProperties config = createYamlConfig("min_duration: 42");

    assertThat(DeclarativeConfigDurationUtil.getDuration(config, "min_duration"))
        .isEqualTo(Duration.ofMillis(42));
  }

  @ParameterizedTest
  @ValueSource(strings = {"other: 1", "min_duration:"})
  void getDuration_returnsNullWhenUnsetForDeclarativeYaml(String propertyLine) {
    DeclarativeConfigProperties config = createYamlConfig(propertyLine);

    assertThat(DeclarativeConfigDurationUtil.getDuration(config, "min_duration")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"other: [1, 2]", "min_duration: null\nother: [1, 2]"})
  void getDuration_doesNotWarnAboutUnrelatedValues(String propertyLines) {
    DeclarativeConfigProperties config = createYamlConfig(propertyLines);
    Logger logger = Logger.getLogger(YamlDeclarativeConfigProperties.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      assertThat(DeclarativeConfigDurationUtil.getDuration(config, "min_duration")).isNull();
      verifyNoInteractions(handler);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "42ms",
        "'42'",
        "42.5",
        "true",
        "[42]",
        "['42ms']",
        "[true]",
        "[42.5]",
        "[]",
        "{value: 42}",
        "{}",
        "[{value: 42}]"
      })
  void getDuration_rejectsNonIntegerValuesForDeclarativeYaml(String value) {
    DeclarativeConfigProperties config = createYamlConfig("min_duration: " + value);

    assertThatThrownBy(() -> DeclarativeConfigDurationUtil.getDuration(config, "min_duration"))
        .isInstanceOf(DeclarativeConfigException.class)
        .hasMessage(
            "Invalid duration for min_duration: expected an integer number of milliseconds");
  }

  private static DeclarativeConfigProperties createYamlConfig(String propertyLine) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    inferred_spans:\n"
            + "      "
            + propertyLine.replace("\n", "\n      ")
            + "\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    return SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
        .getInstrumentationConfig()
        .getStructured("java")
        .getStructured("inferred_spans");
  }
}
