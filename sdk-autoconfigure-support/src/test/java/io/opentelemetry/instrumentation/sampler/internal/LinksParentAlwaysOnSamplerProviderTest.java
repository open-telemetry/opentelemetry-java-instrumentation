/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinksParentAlwaysOnSamplerProviderTest {

  @Test
  void registeredViaServiceLoader() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.sampler", "linksbased_parentbased_always_on");
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");

    AutoConfiguredOpenTelemetrySdk.builder()
        .addPropertiesSupplier(() -> properties)
        .addSamplerCustomizer(
            (sampler, config) -> {
              assertThat(sampler.getDescription())
                  .isEqualTo(
                      new LinksBasedSampler(Sampler.parentBased(Sampler.alwaysOn()))
                          .getDescription());
              return sampler;
            })
        .build();
  }
}
