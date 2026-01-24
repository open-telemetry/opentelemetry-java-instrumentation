/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentelemetry.instrumentation.api.internal.Initializer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentDistributionConfig {
  @SuppressWarnings("ConstantField") // needs to be mutable for @Initializer
  private static AgentDistributionConfig INSTANCE = new AgentDistributionConfig();

  @JsonProperty("indy/development")
  protected boolean indyEnabled = false;

  // This property may be set to force synchronous AgentListener#afterAgent() execution: the
  // condition for delaying the AgentListener initialization is pretty broad and in case it covers
  // too much javaagent users can file a bug, force sync execution by setting this property to true
  // and continue using the javaagent
  @JsonProperty("force_synchronous_agent_listeners/development")
  protected boolean forceSynchronousAgentListeners = false;

  @JsonProperty("exclude_classes")
  protected List<String> excludeClasses = new ArrayList<>();

  @JsonProperty("exclude_class_loaders")
  protected List<String> excludeClassLoaders = new ArrayList<>();

  @JsonProperty("instrumentation")
  private final InstrumentationConfig instrumentation = new InstrumentationConfig();

  public static AgentDistributionConfig get() {
    return INSTANCE;
  }

  // Visible for testing
  public static void resetForTest() {
    INSTANCE = new AgentDistributionConfig();
  }

  /**
   * Creates a new instance for testing or fallback configuration scenarios.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static AgentDistributionConfig create() {
    return new AgentDistributionConfig();
  }

  /**
   * Creates a new instance from ConfigProperties.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static AgentDistributionConfig fromConfigProperties(
      ConfigProperties configProperties,
      boolean indyEnabled,
      boolean forceSynchronousAgentListeners,
      List<String> excludeClasses,
      List<String> excludeClassLoaders) {
    return new ConfigPropertiesAgentDistributionConfig(
        configProperties,
        indyEnabled,
        forceSynchronousAgentListeners,
        excludeClasses,
        excludeClassLoaders);
  }

  @Initializer
  public static void set(AgentDistributionConfig distributionConfig) {
    INSTANCE = distributionConfig;
  }

  /**
   * Returns whether the given instrumentation is enabled.
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code Boolean.TRUE} if the instrumentation is enabled, {@code Boolean.FALSE} if it is
   *     disabled, or {@code null} if the default setting should be used
   */
  @Nullable
  public Boolean getInstrumentationEnabled(String instrumentationName) {
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

  /**
   * Returns whether instrumentations are enabled by default.
   *
   * @return {@code true} if instrumentations are enabled by default, {@code false} otherwise
   */
  public boolean isInstrumentationDefaultEnabled() {
    return instrumentation.isDefaultEnabled();
  }

  /**
   * Returns whether the given instrumentation is explicitly enabled (i.e., not relying on the
   * default setting).
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code true} if the instrumentation is explicitly enabled, {@code false} otherwise
   */
  public boolean isInstrumentationEnabledExplicitly(String instrumentationName) {
    return Boolean.TRUE.equals(getInstrumentationEnabled(instrumentationName));
  }

  /**
   * Returns whether the given instrumentation is enabled, falling back to the default setting if
   * not explicitly configured.
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code true} if the instrumentation is enabled, {@code false} otherwise
   */
  public boolean isInstrumentationEnabled(String instrumentationName) {
    Boolean enabled = getInstrumentationEnabled(instrumentationName);
    if (enabled != null) {
      return enabled;
    }
    return isInstrumentationDefaultEnabled();
  }

  public InstrumentationConfig getInstrumentation() {
    return instrumentation;
  }

  public boolean isIndyEnabled() {
    return indyEnabled;
  }

  public void setIndyEnabled(boolean indyEnabled) {
    this.indyEnabled = indyEnabled;
  }

  public boolean isForceSynchronousAgentListeners() {
    return forceSynchronousAgentListeners;
  }

  public void setForceSynchronousAgentListeners(boolean forceSynchronousAgentListeners) {
    this.forceSynchronousAgentListeners = forceSynchronousAgentListeners;
  }

  public List<String> getExcludeClasses() {
    return excludeClasses;
  }

  public void setExcludeClasses(List<String> excludeClasses) {
    this.excludeClasses = excludeClasses;
  }

  public List<String> getExcludeClassLoaders() {
    return excludeClassLoaders;
  }

  public void setExcludeClassLoaders(List<String> excludeClassLoaders) {
    this.excludeClassLoaders = excludeClassLoaders;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class InstrumentationConfig {
    @JsonProperty("default_enabled")
    private boolean defaultEnabled = true;

    @JsonProperty("disabled")
    private final List<String> disabled = new ArrayList<>();

    @JsonProperty("enabled")
    private final List<String> enabled = new ArrayList<>();

    public List<String> getDisabled() {
      return disabled;
    }

    public List<String> getEnabled() {
      return enabled;
    }

    public boolean isDefaultEnabled() {
      return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
      this.defaultEnabled = defaultEnabled;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  private static final class ConfigPropertiesAgentDistributionConfig
      extends AgentDistributionConfig {
    private final ConfigProperties configProperties;

    ConfigPropertiesAgentDistributionConfig(
        ConfigProperties configProperties,
        boolean indyEnabled,
        boolean forceSynchronousAgentListeners,
        List<String> excludeClasses,
        List<String> excludeClassLoaders) {
      this.configProperties = configProperties;
      this.indyEnabled = indyEnabled;
      this.forceSynchronousAgentListeners = forceSynchronousAgentListeners;
      this.excludeClasses.addAll(excludeClasses);
      this.excludeClassLoaders.addAll(excludeClassLoaders);
    }

    @Override
    @Nullable
    public Boolean getInstrumentationEnabled(String instrumentationName) {
      return configProperties.getBoolean(
          "otel.instrumentation." + instrumentationName + ".enabled");
    }

    @Override
    public boolean isInstrumentationDefaultEnabled() {
      return configProperties.getBoolean("otel.instrumentation.common.default-enabled", true);
    }
  }

  AgentDistributionConfig() {}
}
