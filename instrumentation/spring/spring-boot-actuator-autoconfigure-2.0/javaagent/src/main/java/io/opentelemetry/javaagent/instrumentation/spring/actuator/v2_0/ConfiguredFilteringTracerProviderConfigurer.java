/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.samplers.Sampler;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class ConfiguredFilteringTracerProviderConfigurer
    implements AutoConfigurationCustomizerProvider {
  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        ConfiguredFilteringTracerProviderConfigurer::configure);
  }

  private static SdkTracerProviderBuilder configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {
    String nameFilterRegex = config.getString("otel.instrumentation.sampler.name.filter.regex");
    String httpTargetFilterRegex = config.getString("otel.instrumentation.sampler.http.target.filter.regex");

    if (nameFilterRegex != null || httpTargetFilterRegex != null) {
      sdkTracerProviderBuilder.setSampler(
          new PatternFilteringSampler(nameFilterRegex, httpTargetFilterRegex, Sampler.alwaysOn()));
    }

    return sdkTracerProviderBuilder;
  }
}
