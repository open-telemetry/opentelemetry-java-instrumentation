/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.opentelemetry.javaagent.bootstrap.InstrumentedTaskClasses;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.javaagent.tooling.asyncannotationsupport.WeakRefAsyncOperationEndStrategies;
import io.opentelemetry.javaagent.tooling.config.ConfigPropertiesBridge;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesBuilderImpl;
import io.opentelemetry.javaagent.tooling.util.Trie;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import java.lang.instrument.Instrumentation;
import java.util.List;

import static io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller.installOpenTelemetrySdk;
import static io.opentelemetry.javaagent.tooling.SafeServiceLoader.loadOrdered;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@TargetClass(AgentInstaller.class)
public final class Target_io_opentelemetry_javaagent_tooling_AgentInstaller {
  @SuppressWarnings("ConstantField")
  @Alias
  static String JAVAAGENT_ENABLED_CONFIG;

  @Alias private static io.opentelemetry.javaagent.bootstrap.PatchLogger logger;

  @Substitute
  public static void installBytebuddyAgent(
      Instrumentation inst, ClassLoader extensionClassLoader, EarlyInitAgentConfig earlyConfig) {
    logVersionInfo();
    if (earlyConfig.getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {
      List<AgentListener> agentListeners = loadOrdered(AgentListener.class, extensionClassLoader);
      installBytebuddyAgent(inst, extensionClassLoader, agentListeners);
    } else {
      logger.fine("Tracing is disabled, not installing instrumentations.");
    }
  }

  @Substitute
  private static void installBytebuddyAgent(
      Instrumentation inst,
      ClassLoader extensionClassLoader,
      Iterable<AgentListener> agentListeners) {
    WeakRefAsyncOperationEndStrategies.initialize();

    // If noop OpenTelemetry is enabled, autoConfiguredSdk will be null and AgentListeners are not
    // called
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        installOpenTelemetrySdk(extensionClassLoader);

    ConfigProperties sdkConfig = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    InstrumentationConfig.internalInitializeConfig(new ConfigPropertiesBridge(sdkConfig));
    copyNecessaryConfigToSystemProperties(sdkConfig);

    setBootstrapPackages(sdkConfig, extensionClassLoader);
    /*ConfiguredResourceAttributesHolder.initialize(
    SdkAutoconfigureAccess.getResourceAttributes(autoConfiguredSdk));*/

    for (BeforeAgentListener agentListener :
        loadOrdered(BeforeAgentListener.class, extensionClassLoader)) {
      agentListener.beforeAgent(autoConfiguredSdk);
    }

    int numberOfLoadedExtensions = 0;
    for (AgentExtension agentExtension : loadOrdered(AgentExtension.class, extensionClassLoader)) {
      if (logger.isLoggable(FINE)) {
        logger.log(
            FINE,
            "Loading extension {0} [class {1}]",
            new Object[] {agentExtension.extensionName(), agentExtension.getClass().getName()});
      }
      try {
        numberOfLoadedExtensions++;
      } catch (Exception | LinkageError e) {
        logger.log(
            SEVERE,
            "Unable to load extension "
                + agentExtension.extensionName()
                + " [class "
                + agentExtension.getClass().getName()
                + "]",
            e);
      }
    }
    logger.log(FINE, "Installed {0} extension(s)", numberOfLoadedExtensions);
    // configureIgnoredTypes here
    IgnoredTypesBuilderImpl builder = new IgnoredTypesBuilderImpl();
    for (IgnoredTypesConfigurer configurer :
        loadOrdered(IgnoredTypesConfigurer.class, extensionClassLoader)) {
      configurer.configure(builder, sdkConfig);
    }

    Trie<Boolean> ignoredTasksTrie = builder.buildIgnoredTasksTrie();
    InstrumentedTaskClasses.setIgnoredTaskClassesPredicate(ignoredTasksTrie::contains);
    // configureIgnoredTypes
    addHttpServerResponseCustomizers(extensionClassLoader);

    runAfterAgentListeners(agentListeners, autoConfiguredSdk);
  }

  @Alias
  private static native void logVersionInfo();

  @Alias
  private static native void copyNecessaryConfigToSystemProperties(ConfigProperties config);

  @Alias
  private static native void setBootstrapPackages(
      ConfigProperties config, ClassLoader extensionClassLoader);

  @Alias
  private static native void addHttpServerResponseCustomizers(ClassLoader extensionClassLoader);

  @Alias
  private static native void runAfterAgentListeners(
      Iterable<AgentListener> agentListeners, AutoConfiguredOpenTelemetrySdk autoConfiguredSdk);
}
