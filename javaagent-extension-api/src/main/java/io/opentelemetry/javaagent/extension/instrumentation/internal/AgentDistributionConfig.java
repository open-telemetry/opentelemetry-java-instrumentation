/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentDistributionConfig {
  private static volatile AgentDistributionConfig instance = new AgentDistributionConfig();

  private static volatile boolean initialized;

  @JsonProperty("indy/development")
  private final boolean indyEnabled;

  // This property may be set to force synchronous AgentListener#afterAgent() execution: the
  // condition for delaying the AgentListener initialization is pretty broad and in case it covers
  // too much javaagent users can file a bug, force sync execution by setting this property to true
  // and continue using the javaagent
  @JsonProperty("force_synchronous_agent_listeners/development")
  private final boolean forceSynchronousAgentListeners;

  @JsonProperty("exclude_classes")
  private final List<String> excludeClasses;

  @JsonProperty("exclude_class_loaders")
  private final List<String> excludeClassLoaders;

  @JsonProperty("instrumentation")
  private final InstrumentationConfig instrumentation;

  public static AgentDistributionConfig get() {
    return instance;
  }

  // Only used by tests
  public static void resetForTest() {
    instance = new AgentDistributionConfig();
    initialized = false;
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
  public static AgentDistributionConfig fromConfigProperties(ConfigProperties configProperties) {
    return new ConfigPropertiesAgentDistributionConfig(configProperties);
  }

  public static void set(AgentDistributionConfig distributionConfig) {
    if (initialized) {
      throw new IllegalStateException("AgentDistributionConfig has already been initialized");
    }
    initialized = true;
    instance = distributionConfig;
  }

  @JsonCreator
  AgentDistributionConfig(
      @JsonProperty("indy/development") Boolean indyEnabled,
      @JsonProperty("force_synchronous_agent_listeners/development")
          Boolean forceSynchronousAgentListeners,
      @JsonProperty("exclude_classes") List<String> excludeClasses,
      @JsonProperty("exclude_class_loaders") List<String> excludeClassLoaders,
      @JsonProperty("instrumentation") InstrumentationConfig instrumentation) {
    this.indyEnabled = indyEnabled != null ? indyEnabled : false;
    this.forceSynchronousAgentListeners =
        forceSynchronousAgentListeners != null ? forceSynchronousAgentListeners : false;
    this.excludeClasses =
        excludeClasses != null ? new ArrayList<>(excludeClasses) : new ArrayList<>();
    this.excludeClassLoaders =
        excludeClassLoaders != null ? new ArrayList<>(excludeClassLoaders) : new ArrayList<>();
    this.instrumentation = instrumentation != null ? instrumentation : new InstrumentationConfig();
  }

  // Default constructor for testing
  AgentDistributionConfig() {
    this(null, null, null, null, null);
  }

  /**
   * Returns whether instrumentations are enabled by default.
   *
   * @return {@code true} if instrumentations are enabled by default, {@code false} otherwise
   */
  // TODO remove after
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/16087
  public boolean isInstrumentationDefaultEnabled() {
    return instrumentation.isDefaultEnabled();
  }

  /**
   * Returns whether any of the given instrumentations is enabled, falling back to {@code
   * defaultEnabled} if none of the names are explicitly configured.
   *
   * <p>Names are checked in order; the first name found in either the disabled or enabled list
   * wins. For any given name, disabled takes priority over enabled.
   *
   * @param names the instrumentation names to check
   * @param defaultEnabled the default to use if no name is explicitly configured
   * @return {@code true} if the instrumentation is enabled, {@code false} otherwise
   */
  public boolean isInstrumentationEnabled(Iterable<String> names, boolean defaultEnabled) {
    for (String name : names) {
      String normalizedName = name.replace('-', '_');
      if (instrumentation.getDisabled().contains(normalizedName)) {
        return false;
      }
      if (instrumentation.getEnabled().contains(normalizedName)) {
        return true;
      }
    }
    return defaultEnabled;
  }

  /**
   * Returns whether the given instrumentation is enabled, falling back to {@code defaultEnabled} if
   * not explicitly configured.
   */
  public boolean isInstrumentationEnabled(String name, boolean defaultEnabled) {
    return isInstrumentationEnabled(singletonList(name), defaultEnabled);
  }

  /**
   * Returns whether the given instrumentation is enabled, falling back to {@link
   * #isInstrumentationDefaultEnabled()} if not explicitly configured.
   */
  public boolean isInstrumentationEnabled(String name) {
    return isInstrumentationEnabled(singletonList(name), isInstrumentationDefaultEnabled());
  }

  // Only used by tests
  InstrumentationConfig getInstrumentation() {
    return instrumentation;
  }

  public boolean isIndyEnabled() {
    return indyEnabled;
  }

  public boolean isForceSynchronousAgentListeners() {
    return forceSynchronousAgentListeners;
  }

  public List<String> getExcludeClasses() {
    return excludeClasses;
  }

  public List<String> getExcludeClassLoaders() {
    return excludeClassLoaders;
  }

  static class InstrumentationConfig {
    @JsonProperty("default_enabled")
    private final boolean defaultEnabled;

    @JsonProperty("disabled")
    private final Set<String> disabled;

    @JsonProperty("enabled")
    private final Set<String> enabled;

    @JsonCreator
    InstrumentationConfig(
        @JsonProperty("default_enabled") Boolean defaultEnabled,
        @JsonProperty("disabled") List<String> disabled,
        @JsonProperty("enabled") List<String> enabled) {
      this.defaultEnabled = defaultEnabled != null ? defaultEnabled : true;
      this.disabled = disabled != null ? new HashSet<>(disabled) : new HashSet<>();
      this.enabled = enabled != null ? new HashSet<>(enabled) : new HashSet<>();
    }

    // Default constructor
    InstrumentationConfig() {
      this(null, null, null);
    }

    Set<String> getDisabled() {
      return disabled;
    }

    Set<String> getEnabled() {
      return enabled;
    }

    boolean isDefaultEnabled() {
      return defaultEnabled;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  private static final class ConfigPropertiesAgentDistributionConfig
      extends AgentDistributionConfig {
    private final ConfigProperties configProperties;

    ConfigPropertiesAgentDistributionConfig(ConfigProperties configProperties) {
      super(
          configProperties.getBoolean("otel.javaagent.experimental.indy", false),
          configProperties.getBoolean(
              "otel.javaagent.experimental.force-synchronous-agent-listeners", false),
          configProperties.getList("otel.javaagent.exclude-classes"),
          configProperties.getList("otel.javaagent.exclude-class-loaders"),
          null);
      this.configProperties = configProperties;
    }

    @Override
    public boolean isInstrumentationDefaultEnabled() {
      return configProperties.getBoolean("otel.instrumentation.common.default-enabled", true);
    }

    @Override
    public boolean isInstrumentationEnabled(Iterable<String> names, boolean defaultEnabled) {
      for (String name : names) {
        Boolean enabled = configProperties.getBoolean("otel.instrumentation." + name + ".enabled");
        if (enabled != null) {
          return enabled;
        }
      }
      return defaultEnabled;
    }
  }
}
