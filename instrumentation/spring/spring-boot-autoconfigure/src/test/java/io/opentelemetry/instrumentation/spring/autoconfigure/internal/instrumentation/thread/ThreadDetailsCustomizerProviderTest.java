/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreadDetailsCustomizerProviderTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "true, true",
        "false, false",
        "NULL, false", // distribution node not set
        ", false", // capture_thread_attributes key present with no value
        "invalid, false",
      })
  @SuppressWarnings("StringConcatToTextBlock") // latest dep allows text blocks
  void isEnabled(String propertyValue, boolean expected) {
    String enabled =
        propertyValue == null
            ? ""
            : "distribution:\n"
                + "  spring_starter:\n"
                + "    capture_thread_attributes: "
                + propertyValue
                + "\n";

    String yaml = "file_format: \"1.1\"\n" + enabled;

    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));

    assertThat(new ThreadDetailsCustomizerProvider().isEnabled(model)).isEqualTo(expected);
  }

  @Test
  void ignoresOldName() {
    String yaml =
        "file_format: \"1.1\"\n"
            + "distribution:\n"
            + "  spring_starter:\n"
            + "    thread_details_enabled: true\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));

    assertThat(new ThreadDetailsCustomizerProvider().isEnabled(model)).isFalse();
  }
}
