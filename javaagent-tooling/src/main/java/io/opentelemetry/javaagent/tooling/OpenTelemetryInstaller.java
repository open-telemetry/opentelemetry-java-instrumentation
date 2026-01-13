/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.bootstrap.internal.AgentEnabledInstrumentations;
import io.opentelemetry.javaagent.tooling.config.AgentDistributionConfig;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import java.util.List;
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
      ConfigProvider configProvider = ConfigPropertiesBackedConfigProvider.create(configProperties);
      sdk = new ExtendedOpenTelemetrySdkWrapper(sdk, configProvider);
      AgentDistributionConfig.set(configProvider.getInstrumentationConfig("javaagent"));
    } else {
      // Provide a fake ConfigProperties until we have migrated all runtime configuration
      // access to use declarative configuration API
      configProperties =
          getDeclarativeConfigBridgedProperties(((ExtendedOpenTelemetry) sdk).getConfigProvider());
    }

    EnabledInstrumentations enabledInstrumentations =
        declarativeConfigUsed
            ?
            // AgentDistributionConfig is set by the JavaagentDistributionAccessCustomizerProvider
            enabledInstrumentationsFromConfigDistribution(AgentDistributionConfig.get())
            : enabledInstrumentationsFromConfigProperties(configProperties);

    AgentEnabledInstrumentations.set(enabledInstrumentations);

    setForceFlush(sdk);
    GlobalOpenTelemetry.set(sdk);

    return SdkAutoconfigureAccess.create(
        sdk, SdkAutoconfigureAccess.getResource(autoConfiguredSdk), configProperties);
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

  private static EnabledInstrumentations enabledInstrumentationsFromConfigDistribution(
      DeclarativeConfigProperties distribution) {
    // Should not be parsed for each call
    List<String> enabledModules = null;
    List<String> disabledModules = null;

    DeclarativeConfigProperties instrumentation = distribution.getStructured("instrumentation");
    if (instrumentation != null) {
      disabledModules = instrumentation.getScalarList("disabled", String.class);
      enabledModules = instrumentation.getScalarList("enabled", String.class);
    }

    List<String> disabled = disabledModules;
    List<String> enabled = enabledModules;

    boolean isDefaultEnabled =
        distribution.get("instrumentation").getBoolean("default_enabled", true);

    return new EnabledInstrumentations() {
      @Nullable
      @Override
      public Boolean getEnabled(String instrumentationName) {
        String normalizedName = instrumentationName.replace('-', '_');

        if (disabled != null && disabled.contains(normalizedName)) {
          return false;
        }

        if (enabled != null && enabled.contains(normalizedName)) {
          return true;
        }
        return null;
      }

      @Override
      public boolean isDefaultEnabled() {
        return isDefaultEnabled;
      }
    };
  }

  // Visible for testing
  static ConfigProperties getDeclarativeConfigBridgedProperties(ConfigProvider configProvider) {
    return new DeclarativeConfigPropertiesBridgeBuilder()
        .addMapping("otel.javaagent", "agent")
        // these properties are used to initialize the SDK before the configuration file
        // is loaded for consistency, we pass them to the bridge, so that they can be read
        // later with the same value from the {@link DeclarativeConfigPropertiesBridge}
        .addOverride("otel.javaagent.debug", EarlyInitAgentConfig.get().isDebug())
        .addOverride("otel.javaagent.logging", EarlyInitAgentConfig.get().getLogging())
        .buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
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
