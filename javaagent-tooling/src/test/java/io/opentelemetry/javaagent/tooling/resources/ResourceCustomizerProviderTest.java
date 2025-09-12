/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
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
                        "{\"attributes\":[],\"detection/development\":{\"detectors\":["
                            + "{\"service\":null},"
                            + "{\"opentelemetry-javaagent-distribution\":null}"
                            + "]}}");
              } catch (JsonProcessingException e) {
                throw new AssertionError(e);
              }
            });
  }
}
