/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.AgentInstaller.JAVAAGENT_ENABLED_CONFIG;
import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;
import java.util.Optional;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTracerProviderConfigurer implements AutoConfigurationCustomizerProvider {
  private static final String ADD_THREAD_DETAILS = "otel.javaagent.add-thread-details";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        AgentTracerProviderConfigurer::configure);
    autoConfigurationCustomizer.addMeterProviderCustomizer(
        AgentTracerProviderConfigurer::configureMeterProvider);
    autoConfigurationCustomizer.addMetricExporterCustomizer(
        (metricExporter, configProperties) -> new NoopMeterExporter());
  }

  private static SdkMeterProviderBuilder configureMeterProvider(
      SdkMeterProviderBuilder meterProviderBuilder, ConfigProperties configProperties) {
    return SdkMeterProvider.builder();
  }

  private static SdkTracerProviderBuilder configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {
    if (!config.getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {
      return sdkTracerProviderBuilder;
    }

    // Register additional thread details logging span processor
    if (config.getBoolean(ADD_THREAD_DETAILS, true)) {
      sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
    }
    sdkTracerProviderBuilder.addSpanProcessor(new HeliosProcessor());

    Optional<Double> heliosRatioProperty = getHeliosSamplingRationProperty();
    heliosRatioProperty.ifPresent(
        ratioProperty -> sdkTracerProviderBuilder.setSampler(new HeliosSampler(ratioProperty)));

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

  private static Optional<Double> getHeliosSamplingRationProperty() {
    try {
      String ratio = System.getenv(String.valueOf(RatioProperty.HS_SAMPLING_RATIO));
      if (ratio == null) {
        ratio = System.getProperty(RatioProperty.HS_SAMPLING_RATIO.propertyName());
        if (ratio != null) {
          return Optional.of(Double.parseDouble(ratio));
        }
      }
    } catch (Exception e) {
      System.out.println("Exception while getting ratio property: " + e);
    }

    return Optional.empty();
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
