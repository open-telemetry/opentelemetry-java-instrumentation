/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.javaagent.extension.DeclarativeConfigPropertiesBridge;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.resources.Resource;
import java.io.FileInputStream;
import java.io.IOException;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class DeclarativeConfigEarlyInitAgentConfig implements EarlyInitAgentConfig {
  private final String configurationFile;

  DeclarativeConfigEarlyInitAgentConfig(String configurationFile) {
    this.configurationFile = configurationFile;
  }

  @NotNull
  public static EarlyInitAgentConfig create(String configurationFile) {
    return new DeclarativeConfigEarlyInitAgentConfig(configurationFile);
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return ConfigPropertiesUtil.getString(propertyName);
  }

  @Override
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    return ConfigPropertiesUtil.getBoolean(propertyName, defaultValue);
  }

  @Override
  public int getInt(String propertyName, int defaultValue) {
    return ConfigPropertiesUtil.getInt(propertyName, defaultValue);
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
    OpenTelemetryConfigurationModel model = loadConfigurationModel(configurationFile);
    SdkConfigProvider configProvider = SdkConfigProvider.create(model);
    DeclarativeConfigPropertiesBridge configProperties =
        new DeclarativeConfigPropertiesBridge(configProvider);
    OpenTelemetrySdk sdk =
        DeclarativeConfiguration.create(
            model, SpiHelper.serviceComponentLoader(extensionClassLoader));
    Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
    GlobalOpenTelemetry.set(sdk);
    GlobalConfigProvider.set(configProvider);

    return SdkAutoconfigureAccess.create(
        sdk, Resource.getDefault(), configProperties, configProvider);
  }

}
