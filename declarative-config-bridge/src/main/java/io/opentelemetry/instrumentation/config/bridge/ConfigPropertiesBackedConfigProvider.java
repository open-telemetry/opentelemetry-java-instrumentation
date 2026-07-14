/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ConfigProvider} implementation backed by {@link ConfigProperties}.
 *
 * <p>This allows instrumentations to always use {@code ExtendedOpenTelemetry.getConfigProvider()}
 * regardless of whether the user started with system properties or YAML.
 */
public final class ConfigPropertiesBackedConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new ConfigPropertiesBackedConfigProvider(
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties));
  }

  public static Builder builder() {
    return new Builder();
  }

  private ConfigPropertiesBackedConfigProvider(DeclarativeConfigProperties instrumentationConfig) {
    this.instrumentationConfig = instrumentationConfig;
  }

  public static final class Builder {
    private final Map<String, String> mappings = new HashMap<>();
    private String accessRoot =
        ConfigPropertiesBackedDeclarativeConfigProperties.DEFAULT_ACCESS_ROOT;
    private String resultPrefix =
        ConfigPropertiesBackedDeclarativeConfigProperties.DEFAULT_RESULT_PREFIX;

    private Builder() {}

    /**
     * Adds a mapping from a declarative property path to a flat {@link ConfigProperties} key.
     *
     * <p>This is useful when a component is configured under a custom declarative path but still
     * wants to read an existing flat property name. For example, contrib's inferred spans component
     * uses declarative keys like {@code backup_diagnostic_files} while reading {@code
     * otel.inferred.spans.backup.diagnostic.files} from flat config.
     */
    @CanIgnoreReturnValue
    public Builder addMapping(String declarativeProperty, String configProperty) {
      mappings.put(declarativeProperty, configProperty);
      return this;
    }

    /**
     * Sets the declarative path prefix that this bridge reads from and the flat-property prefix it
     * resolves to.
     *
     * <p>This lets callers bridge a subtree directly instead of always starting from {@code java.}
     * -> {@code otel.instrumentation.}. For example, contrib's inferred spans autoconfigure path
     * reads the component root directly and resolves it against {@code otel.inferred.spans.}.
     */
    @CanIgnoreReturnValue
    public Builder setAccessPath(String accessRoot, String resultPrefix) {
      this.accessRoot = accessRoot;
      this.resultPrefix = resultPrefix;
      return this;
    }

    public ConfigProvider build(ConfigProperties configProperties) {
      return new ConfigPropertiesBackedConfigProvider(
          ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
              configProperties, mappings, accessRoot, resultPrefix));
    }
  }

  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }
}
