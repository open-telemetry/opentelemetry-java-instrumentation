/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.testing.internal.jackson.core.JsonProcessingException;
import io.opentelemetry.testing.internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResourceCustomizerProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void customize() {
    new ResourceCustomizerProvider()
        .customize(
            customizer -> {
              OpenTelemetryConfigurationModel configurationModel =
                  customizer.apply(new OpenTelemetryConfigurationModel());

              try {
                assertThat(objectMapper.writeValueAsString(configurationModel.getResource()))
                    .isEqualTo(
                        "{\"attributes\":[],\"detectionDevelopment\":{\"attributes\":null,\"detectors\":["
                            + "{\"additionalProperties\":{\"distribution\":null}},"
                            + "{\"additionalProperties\":{\"service\":null}}]},"
                            + "\"schemaUrl\":null,\"attributesList\":null}");
              } catch (JsonProcessingException e) {
                throw new AssertionError(e);
              }
            });
  }
}
