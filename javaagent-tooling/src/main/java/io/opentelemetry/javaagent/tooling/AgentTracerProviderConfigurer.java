/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.AgentInstaller.JAVAAGENT_ENABLED_CONFIG;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTracerProviderConfigurer implements AutoConfigurationCustomizerProvider {
  private static final String ADD_THREAD_DETAILS = "otel.javaagent.add-thread-details";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        AgentTracerProviderConfigurer::configure);
  }

  private static SdkTracerProviderBuilder configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {
    if (!Config.get().getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {
      return sdkTracerProviderBuilder;
    }

    // Register additional thread details logging span processor
    if (Config.get().getBoolean(ADD_THREAD_DETAILS, true)) {
      sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
    }
    sdkTracerProviderBuilder.addSpanProcessor(new HeliosProcessor());

    String heliosRatioProperty = getHeliosSamplingRationProperty();
    if (heliosRatioProperty != null) {
      sdkTracerProviderBuilder.setSampler(
          new HeliosSampler(Double.parseDouble(heliosRatioProperty)));
    }

    maybeEnableLoggingExporter(sdkTracerProviderBuilder);

    return sdkTracerProviderBuilder;
  }

  private static void maybeEnableLoggingExporter(SdkTracerProviderBuilder builder) {
    if (AgentConfig.get().isDebugModeEnabled()) {
      // don't install another instance if the user has already explicitly requested it.
      if (loggingExporterIsNotAlreadyConfigured()) {
        builder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
      }
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured() {
    return !Config.get()
        .getList("otel.traces.exporter", Collections.emptyList())
        .contains("logging");
  }

  private static String getHeliosSamplingRationProperty() {
    String ratio = System.getenv(String.valueOf(RatioProperty.HS_SAMPLING_RATIO));
    if (ratio == null) {
      ratio = System.getProperty(RatioProperty.HS_SAMPLING_RATIO.propertyName());
    }

    return ratio;
  }

  private enum RatioProperty {
    HS_SAMPLING_RATIO("hs.sampling.ratio");

    private final String propertyName;

    RatioProperty(String propertyName) {
      this.propertyName = propertyName;
    }

    private String propertyName() {
      return propertyName;
    }
  }
}
