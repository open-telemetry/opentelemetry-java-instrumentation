/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.extension.DeclarativeConfigPropertiesBridge;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.resources.Resource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class DeclarativeConfigEarlyInitAgentConfig implements EarlyInitAgentConfig {
  private final OpenTelemetryConfigurationModel configurationModel;
  private final ConfigProperties declarativeConfigProperties;
  private final SdkConfigProvider configProvider;

  DeclarativeConfigEarlyInitAgentConfig(OpenTelemetryConfigurationModel model) {
    this.configurationModel = model;
    configProvider = SdkConfigProvider.create(configurationModel);
    this.declarativeConfigProperties =
        new DeclarativeConfigPropertiesBridge(
            configProvider, this.configurationModel.getLogLevel());
  }

  @NotNull
  public static EarlyInitAgentConfig create(String configurationFile) {
    OpenTelemetryConfigurationModel model = loadConfigurationModel(configurationFile);
    if (model == null) {
      // broken configuration file, return a broken config, error is logged later
      return new BrokenEarlyInitAgentConfig();
    }

    return new DeclarativeConfigEarlyInitAgentConfig(model);
  }

  @Override
  public boolean isAgentEnabled() {
    return !Objects.equals(configurationModel.getDisabled(), true);
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return declarativeConfigProperties.getString(propertyName);
  }

  @Override
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    return declarativeConfigProperties.getBoolean(propertyName, defaultValue);
  }

  @Override
  public int getInt(String propertyName, int defaultValue) {
    return declarativeConfigProperties.getInt(propertyName, defaultValue);
  }

  @Nullable
  private static OpenTelemetryConfigurationModel loadConfigurationModel(String configurationFile) {
    try (FileInputStream fis = new FileInputStream(configurationFile)) {
      return DeclarativeConfiguration.parse(fis);
    } catch (IOException e) {
      ErrorBuffer.addErrorMessage(
          "Error reading configuration file: " + configurationFile + ". " + e.getMessage());
      return null;
    }
  }

  @Override
  public AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(ClassLoader extensionClassLoader) {
    OpenTelemetrySdk sdk =
        DeclarativeConfiguration.create(
            this.configurationModel, SpiHelper.serviceComponentLoader(extensionClassLoader));
    Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
    GlobalOpenTelemetry.set(sdk);
    GlobalConfigProvider.set(this.configProvider);

    setForceFlush(sdk);

    return SdkAutoconfigureAccess.create(
        sdk, Resource.getDefault(), this.declarativeConfigProperties, this.configProvider);
  }

  static void setForceFlush(OpenTelemetrySdk sdk) {
    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode logsResult = sdk.getSdkLoggerProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult, logsResult))
              .join(timeout, unit);
        });
  }
}
