/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.bootstrap.internal.AgentEnabledInstrumentations;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
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
      sdk =
          new ExtendedOpenTelemetrySdkWrapper(
              sdk, ConfigPropertiesBackedConfigProvider.create(configProperties));
      AgentDistributionConfig.set(distributionFromConfigProperties(configProperties));
    } else {
      // Provide a fake ConfigProperties until we have migrated all runtime configuration
      // access to use declarative configuration API
      configProperties =
          getDeclarativeConfigBridgedProperties(((ExtendedOpenTelemetry) sdk).getConfigProvider());
    }

    // AgentDistributionConfig is set by the JavaagentDistributionAccessCustomizerProvider
    AgentEnabledInstrumentations.set(
        declarativeConfigUsed
            ? new DistributionEnabledInstrumentations(
                AgentDistributionConfig.get().getInstrumentation())
            : enabledInstrumentationsFromConfigProperties(configProperties));

    setForceFlush(sdk);
    GlobalOpenTelemetry.set(sdk);

    return SdkAutoconfigureAccess.create(
        sdk, SdkAutoconfigureAccess.getResource(autoConfiguredSdk), configProperties);
  }

  private static AgentDistributionConfig distributionFromConfigProperties(ConfigProperties config) {
    AgentDistributionConfig agentConfig = AgentDistributionConfig.create();

    agentConfig.setIndyEnabled(config.getBoolean("otel.javaagent.experimental.indy", false));

    agentConfig.setForceSynchronousAgentListeners(
        config.getBoolean("otel.javaagent.experimental.force-synchronous-agent-listeners", false));

    agentConfig.setExcludeClasses(config.getList("otel.javaagent.exclude-classes", emptyList()));

    agentConfig.setExcludeClassLoaders(
        config.getList("otel.javaagent.exclude-class-loaders", emptyList()));

    // Populate test configuration
    agentConfig
        .getTest()
        .getAdditionalLibraryIgnoresConfig()
        .setEnabled(
            config.getBoolean("otel.javaagent.testing.additional-library-ignores.enabled", true));

    // Populate exclude classes
    agentConfig
        .getExcludeClasses()
        .addAll(config.getList("otel.javaagent.exclude-classes", emptyList()));

    // Populate exclude class loaders
    agentConfig
        .getExcludeClassLoaders()
        .addAll(config.getList("otel.javaagent.exclude-class-loaders", emptyList()));

    return agentConfig;
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

  private static class DistributionEnabledInstrumentations implements EnabledInstrumentations {
    private final AgentDistributionConfig.Instrumentation instrumentation;

    DistributionEnabledInstrumentations(AgentDistributionConfig.Instrumentation instrumentation) {
      this.instrumentation = instrumentation;
    }

    @Nullable
    @Override
    public Boolean getEnabled(String instrumentationName) {
      String normalizedName = instrumentationName.replace('-', '_');

      List<String> disabled = instrumentation.getDisabled();
      if (disabled != null && disabled.contains(normalizedName)) {
        return false;
      }

      List<String> enabled = instrumentation.getEnabled();
      if (enabled != null && enabled.contains(normalizedName)) {
        return true;
      }
      return null;
    }

    @Override
    public boolean isDefaultEnabled() {
      return instrumentation.isDefaultEnabled();
    }
  }
}
