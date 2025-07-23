/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.extension.internal.ConfigPropertiesFactory;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
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
      return SdkAutoconfigureAccess.create(
          sdk,
          Resource.getDefault(),
          ConfigPropertiesFactory.builder()
              .addTranslation(
                  "otel.instrumentation.common.default-enabled", "common.default.enabled")
              .addTranslation("otel.javaagent", "agent")
              // these properties are used to initialize the SDK before the configuration file
              // is loaded
              // for consistency, we pass them to the bridge, so that they can be read later
              // with the same
              // value from the {@link DeclarativeConfigPropertiesBridge}
              .addFixedValue(
                  "otel.javaagent.debug", earlyConfig.getBoolean("otel.javaagent.debug", false))
              .addFixedValue(
                  "otel.javaagent.logging", earlyConfig.getString("otel.javaagent.logging"))
              .resolveInstrumentationConfig(configProvider.getInstrumentationConfig()),
          configProvider);
    }

    return autoConfiguredSdk;
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
