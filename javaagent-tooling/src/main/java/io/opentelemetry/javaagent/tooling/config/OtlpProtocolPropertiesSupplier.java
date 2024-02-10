/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.Collections;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class OtlpProtocolPropertiesSupplier implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addPropertiesSupplier(
        () -> Collections.singletonMap("otel.exporter.otlp.protocol", "http/protobuf"));
  }

  @Override
  public int order() {
    // make sure it runs BEFORE all the user-provided customizers
    return Integer.MIN_VALUE;
  }
}
