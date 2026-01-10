/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.javaagent.tooling.config.JavaagentDistribution;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    ConfigProvider configProvider;
    EnabledInstrumentations enabledInstrumentations;
    if (configProperties != null) {
      // Provide a fake declarative configuration based on config properties
      // so that declarative configuration API can be used everywhere
      configProvider = ConfigPropertiesBackedConfigProvider.create(configProperties);
      sdk = new ExtendedOpenTelemetrySdkWrapper(sdk, configProvider);
      enabledInstrumentations = enabledInstrumentationsFromConfigProperties(configProperties);
      JavaagentDistribution.set(configProvider.getInstrumentationConfig());
    } else {
      // Provide a fake ConfigProperties until we have migrated all runtime configuration
      // access to use declarative configuration API
      configProvider = ((ExtendedOpenTelemetry) sdk).getConfigProvider();
      configProperties = getDeclarativeConfigBridgedProperties(configProvider);
      // distribution node is set by the JavaagentDistributionAccessCustomizerProvider
      enabledInstrumentations = enabledInstrumentationsFromConfigDistribution(
          Objects.requireNonNull(JavaagentDistribution.get()));
    }

    AgentCommonConfig.setEnabledInstrumentations(enabledInstrumentations);

    setForceFlush(sdk);
    GlobalOpenTelemetry.set(sdk);

    return SdkAutoconfigureAccess.create(
        sdk,
        SdkAutoconfigureAccess.getResource(autoConfiguredSdk),
        configProperties,
        configProvider);
  }

  private static EnabledInstrumentations enabledInstrumentationsFromConfigProperties(
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
        Objects.requireNonNull(distribution)
            .getStructured("instrumentation", empty())
            .getBoolean("default_enabled", true);

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
        .addOverride("otel.instrumentation.common.default-enabled", defaultEnabled(configProvider))
        // these properties are used to initialize the SDK before the configuration file
        // is loaded for consistency, we pass them to the bridge, so that they can be read
        // later with the same value from the {@link DeclarativeConfigPropertiesBridge}
        .addOverride("otel.javaagent.debug", EarlyInitAgentConfig.get().isDebug())
        .addOverride("otel.javaagent.logging", EarlyInitAgentConfig.get().getLogging())
        .buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
  }

  private static boolean defaultEnabled(ConfigProvider configProvider) {
    DeclarativeConfigProperties instrumentationConfig = configProvider.getInstrumentationConfig();
    if (instrumentationConfig == null) {
      return true;
    }

    String mode =
        instrumentationConfig
            .getStructured("java", empty())
            .getStructured("agent", empty())
            .getString("instrumentation_mode", "default");

    switch (mode) {
      case "none":
        return false;
      case "default":
        return true;
      default:
        throw new ConfigurationException("Unknown instrumentation mode: " + mode);
    }
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
