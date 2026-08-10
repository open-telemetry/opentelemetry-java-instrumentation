/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.exporter.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class ZipkinExporterRemovalDeclarativeCustomizerProviderTest {

  private static final String V3_PREVIEW =
      "instrumentation/development:\n  java:\n    common:\n      v3_preview: true\n";

  private static final String ZIPKIN_BATCH_PROCESSOR =
      "tracer_provider:\n  processors:\n    - batch:\n        exporter:\n          zipkin:\n";

  @Test
  void allowsZipkinWhenV3PreviewDisabled() {
    assertThatCode(() -> check(ZIPKIN_BATCH_PROCESSOR)).doesNotThrowAnyException();
  }

  @Test
  void failsOnZipkinBatchProcessorWhenV3PreviewEnabled() {
    assertThatThrownBy(() -> check(V3_PREVIEW + ZIPKIN_BATCH_PROCESSOR))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("zipkin span exporter is not supported");
  }

  @Test
  void failsOnZipkinSimpleProcessorWhenV3PreviewEnabled() {
    assertThatThrownBy(
            () ->
                check(
                    V3_PREVIEW
                        + "tracer_provider:\n  processors:\n    - simple:\n        exporter:\n"
                        + "          zipkin:\n"))
        .isInstanceOf(ConfigurationException.class);
  }

  @Test
  void allowsOtherExportersWhenV3PreviewEnabled() {
    assertThatCode(
            () ->
                check(
                    V3_PREVIEW
                        + "tracer_provider:\n  processors:\n    - batch:\n        exporter:\n"
                        + "          console:\n"))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsMissingTracerProviderWhenV3PreviewEnabled() {
    assertThatCode(() -> check(V3_PREVIEW)).doesNotThrowAnyException();
  }

  @Test
  void runsAfterUserProvidedCustomizers() {
    assertThat(new ZipkinExporterRemovalDeclarativeCustomizerProvider().order())
        .isEqualTo(Integer.MAX_VALUE);
  }

  private static void check(String yaml) {
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(
            new ByteArrayInputStream(("file_format: \"1.1\"\n" + yaml).getBytes(UTF_8)));
    ZipkinExporterRemovalDeclarativeCustomizerProvider.checkZipkinExporter(model);
  }
}
