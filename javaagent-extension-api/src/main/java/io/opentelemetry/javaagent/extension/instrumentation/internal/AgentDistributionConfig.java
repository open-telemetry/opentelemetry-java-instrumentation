/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentelemetry.instrumentation.api.internal.Initializer;
import io.opentelemetry.javaagent.bootstrap.internal.EnabledInstrumentations;
import java.util.ArrayList;
import java.util.List;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class AgentDistributionConfig {
  @SuppressWarnings("ConstantField") // needs to be mutable for @Initializer
  private static AgentDistributionConfig INSTANCE = new AgentDistributionConfig();

  @JsonProperty("indy/development")
  private boolean indyEnabled = false;

  // This property may be set to force synchronous AgentListener#afterAgent() execution: the
  // condition for delaying the AgentListener initialization is pretty broad and in case it covers
  // too much javaagent users can file a bug, force sync execution by setting this property to true
  // and continue using the javaagent
  @JsonProperty("force_synchronous_agent_listeners/development")
  private boolean forceSynchronousAgentListeners = false;

  @JsonProperty("exclude_classes")
  private List<String> excludeClasses = new ArrayList<>();

  @JsonProperty("exclude_class_loaders")
  private List<String> excludeClassLoaders = new ArrayList<>();

  @JsonProperty("instrumentation")
  private final Instrumentation instrumentation = new Instrumentation();

  /**
   * Enabled instrumentations are set later via {@link
   * #setEnabledInstrumentations(EnabledInstrumentations)}.
   */
  @JsonIgnore private EnabledInstrumentations enabledInstrumentations;

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

  @Initializer
  public static void set(AgentDistributionConfig distributionConfig) {
    INSTANCE = distributionConfig;
  }

  public EnabledInstrumentations getEnabledInstrumentations() {
    return enabledInstrumentations;
  }

  @Initializer
  public void setEnabledInstrumentations(EnabledInstrumentations enabledInstrumentations) {
    this.enabledInstrumentations = enabledInstrumentations;
  }

  public Instrumentation getInstrumentation() {
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
  public static class Instrumentation {
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

  AgentDistributionConfig() {}
}
