/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class OtlpProtocolPropertiesSupplier implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addPropertiesSupplier(ConfigurationFile::getProperties);
    autoConfigurationCustomizer.addPropertiesCustomizer(new OtlpProtocolConfigCustomizer());
  }

  @Override
  public int order() {
    // make sure it runs after all the user-provided customizers
    // this is a low-priority configuration customizer, and it has to run after the
    // ConfigurationPropertiesSupplier, which is at Integer.MAX_VALUE (high-priority)
    return Integer.MIN_VALUE;
  }
}
