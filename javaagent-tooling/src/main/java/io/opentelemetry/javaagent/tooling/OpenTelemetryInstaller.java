/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;

public final class OpenTelemetryInstaller {

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  public static AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(
      ClassLoader extensionClassLoader, EarlyInitAgentConfig earlyConfig) {

    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .setResultAsGlobal()
            .setServiceClassLoader(extensionClassLoader)
            .build();
    ConfigProvider configProvider = AutoConfigureUtil.getConfigProvider(autoConfiguredSdk);
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();

    setForceFlush(sdk);

    if (configProvider != null) {
      // We create a new instance of AutoConfiguredOpenTelemetrySdk, which has a ConfigProperties
      // instance that can be used to read properties from the configuration file.
      // This allows most instrumentations to be unaware of which configuration style is used.
      return SdkAutoconfigureAccess.create(
          sdk,
          SdkAutoconfigureAccess.getResource(autoConfiguredSdk),
          getDeclarativeConfigBridgedProperties(earlyConfig, configProvider),
          configProvider);
    }

    return autoConfiguredSdk;
  }

  // Visible for testing
  static ConfigProperties getDeclarativeConfigBridgedProperties(
      EarlyInitAgentConfig earlyConfig, ConfigProvider configProvider) {
    return new DeclarativeConfigPropertiesBridgeBuilder()
        .addMapping("otel.javaagent", "agent")
        .addOverride("otel.instrumentation.common.default-enabled", defaultEnabled(configProvider))
        // these properties are used to initialize the SDK before the configuration file
        // is loaded for consistency, we pass them to the bridge, so that they can be read
        // later with the same value from the {@link DeclarativeConfigPropertiesBridge}
        .addOverride("otel.javaagent.debug", earlyConfig.getBoolean("otel.javaagent.debug", false))
        .addOverride("otel.javaagent.logging", earlyConfig.getString("otel.javaagent.logging"))
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
