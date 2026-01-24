/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.bootstrap.internal.EnabledInstrumentations;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

public final class OpenTelemetryInstaller {

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  public static AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(
      ClassLoader extensionClassLoader) {

    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            // Don't use setResultAsGlobal() - we need to wrap the SDK before setting as global
            .setServiceClassLoader(extensionClassLoader)
            .build();
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();
    ConfigProperties configProperties = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    boolean declarativeConfigUsed = configProperties == null;

    if (!declarativeConfigUsed) {
      // Provide a fake declarative configuration based on config properties
      // so that declarative configuration API can be used everywhere
      sdk =
          new ExtendedOpenTelemetrySdkWrapper(
              sdk, ConfigPropertiesBackedConfigProvider.create(configProperties));
      EnabledInstrumentations enabledInstrumentations =
          enabledInstrumentationsFromConfigProperties(configProperties);
      AgentDistributionConfig.set(
          distributionFromConfigProperties(configProperties, enabledInstrumentations));
    }
    // else: AgentDistributionConfig is set by JavaagentDistributionAccessCustomizerProvider
    // and EnabledInstrumentations is computed lazily from the Instrumentation config

    setForceFlush(sdk);
    GlobalOpenTelemetry.set(sdk);

    return SdkAutoconfigureAccess.create(
        sdk, SdkAutoconfigureAccess.getResource(autoConfiguredSdk), configProperties);
  }

  private static AgentDistributionConfig distributionFromConfigProperties(
      ConfigProperties config, EnabledInstrumentations enabledInstrumentations) {
    return AgentDistributionConfig.builder()
        .indyEnabled(config.getBoolean("otel.javaagent.experimental.indy", false))
        .forceSynchronousAgentListeners(
            config.getBoolean("otel.javaagent.experimental.force-synchronous-agent-listeners", false))
        .excludeClasses(config.getList("otel.javaagent.exclude-classes", emptyList()))
        .excludeClassLoaders(config.getList("otel.javaagent.exclude-class-loaders", emptyList()))
        .enabledInstrumentations(enabledInstrumentations)
        .build();
  }


  // Visible for testing
  public static EnabledInstrumentations enabledInstrumentationsFromConfigProperties(
      ConfigProperties configProperties) {
    boolean isDefaultEnabled =
        configProperties.getBoolean("otel.instrumentation.common.default-enabled", true);

    return new EnabledInstrumentations() {
      @Nullable
      @Override
      public Boolean getEnabled(String instrumentationName) {
        return configProperties.getBoolean(
            "otel.instrumentation." + instrumentationName + ".enabled");
      }

      @Override
      public boolean isDefaultEnabled() {
        return isDefaultEnabled;
      }
    };
  }

  private static void setForceFlush(OpenTelemetrySdk sdk) {
    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode logsResult = sdk.getSdkLoggerProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult, logsResult))
              .join(timeout, unit);
        });
  }

  private OpenTelemetryInstaller() {}
}
