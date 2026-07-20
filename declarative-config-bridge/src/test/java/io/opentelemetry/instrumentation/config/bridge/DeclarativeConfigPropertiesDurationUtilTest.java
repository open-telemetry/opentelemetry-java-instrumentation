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
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeclarativeConfigPropertiesDurationUtilTest {

  @Test
  void getDuration_supportsDurationStringsForConfigPropertiesBackedConfig() {
    DeclarativeConfigProperties config =
        ConfigPropertiesBackedConfigProvider.builder()
            .setAccessPath("", "otel.inferred.spans.")
            .build(
                DefaultConfigProperties.createFromMap(
                    singletonMap("otel.inferred.spans.min.duration", "42ms")))
            .getInstrumentationConfig();

    assertThat(DeclarativeConfigPropertiesDurationUtil.getDuration(config, "min_duration"))
        .isEqualTo(Duration.ofMillis(42));
  }

  @Test
  void getDuration_supportsIntegerMillisForDeclarativeYaml() {
    DeclarativeConfigProperties config = createYamlConfig("min_duration: 42");

    assertThat(DeclarativeConfigPropertiesDurationUtil.getDuration(config, "min_duration"))
        .isEqualTo(Duration.ofMillis(42));
  }

  @Test
  void getDuration_doesNotSupportDurationStringsForDeclarativeYaml() {
    DeclarativeConfigProperties config = createYamlConfig("min_duration: 42ms");

    assertThat(DeclarativeConfigPropertiesDurationUtil.getDuration(config, "min_duration"))
        .isNull();
  }

  private static DeclarativeConfigProperties createYamlConfig(String propertyLine) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    inferred_spans:\n"
            + "      "
            + propertyLine
            + "\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    return SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
        .getInstrumentationConfig()
        .getStructured("java")
        .getStructured("inferred_spans");
  }
}
