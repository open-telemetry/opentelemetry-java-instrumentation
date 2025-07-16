/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.instrumentation.internal.logging.LoggingSpanExporterConfigurer.enableLoggingExporter;
import static io.opentelemetry.javaagent.tooling.AgentInstaller.JAVAAGENT_ENABLED_CONFIG;

import com.google.auto.service.AutoService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.internal.thread.AddThreadDetailsSpanProcessor;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTracerProviderConfigurer implements AutoConfigurationCustomizerProvider {
  private static final String ADD_THREAD_DETAILS = "otel.javaagent.add-thread-details";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        AgentTracerProviderConfigurer::configure);
  }

  @CanIgnoreReturnValue
  private static SdkTracerProviderBuilder configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {
    if (!config.getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {
      return sdkTracerProviderBuilder;
    }

    // Register additional thread details logging span processor
    if (config.getBoolean(ADD_THREAD_DETAILS, true)) {
      sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
    }

    if (AgentConfig.isDebugModeEnabled(config)) {
      enableLoggingExporter(sdkTracerProviderBuilder, config);
    }

    return sdkTracerProviderBuilder;
  }
}
