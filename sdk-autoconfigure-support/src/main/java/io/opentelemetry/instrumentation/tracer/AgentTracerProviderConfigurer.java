/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.tracer;

import static io.opentelemetry.javaagent.tooling.AgentInstaller.JAVAAGENT_ENABLED_CONFIG;
import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

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
    // Spring starter uses "otel.sdk.disabled" to disable the SDK, but this check is enabled
    // by default - so it shouldn't impact the Spring starter.
    if (!config.getBoolean("otel.javaagent.enabled", true)) {
      return sdkTracerProviderBuilder;
    }

    // Register additional thread details logging span processor
    if (config.getBoolean(ADD_THREAD_DETAILS, true)) {
      sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
    }

    maybeEnableLoggingExporter(sdkTracerProviderBuilder, config);

    return sdkTracerProviderBuilder;
  }

  private static void maybeEnableLoggingExporter(
      SdkTracerProviderBuilder builder, ConfigProperties config) {
    if (AgentConfig.isDebugModeEnabled(config)) {
      // don't install another instance if the user has already explicitly requested it.
      if (loggingExporterIsNotAlreadyConfigured(config)) {
        builder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
      }
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured(ConfigProperties config) {
    return !config.getList("otel.traces.exporter", emptyList()).contains("logging");
  }
}
