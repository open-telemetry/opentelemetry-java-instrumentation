/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Loads the shared configuration definitions ("globals") from {@code
 * shared-config-definitions.yaml} and resolves {@code ref} entries in a module's configuration list
 * into full {@link ConfigurationOption}s. It also loads the global configurations: settings read by
 * the agent or the instrumentation API itself rather than by a specific instrumentation, which no
 * module's metadata.yaml would otherwise declare.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SharedConfigurationRegistry {

  private static final String RESOURCE = "/shared-config-definitions.yaml";

  private static final SharedConfigurationRegistry INSTANCE = load();

  private final Map<String, ConfigurationOption> definitions;
  private final Map<String, ConfigurationOption> globalConfigurations;

  private SharedConfigurationRegistry(
      Map<String, ConfigurationOption> definitions,
      Map<String, ConfigurationOption> globalConfigurations) {
    this.definitions = definitions;
    this.globalConfigurations = globalConfigurations;
  }

  public static SharedConfigurationRegistry getInstance() {
    return INSTANCE;
  }

  /** Returns the shared definitions keyed by id. */
  public Map<String, ConfigurationOption> definitions() {
    return definitions;
  }

  /**
   * Returns the global configurations keyed by id. Unlike {@link #definitions()}, these are not
   * referenced from a module's metadata.yaml; the doc generator always includes them.
   */
  public Map<String, ConfigurationOption> globalConfigurations() {
    return globalConfigurations;
  }

  /**
   * Expands each {@code ref} entry into its shared definition (tagged with the definition id) and
   * passes non-ref entries through unchanged. Throws {@link IllegalArgumentException} on an unknown
   * ref id so a mistyped ref fails the build rather than silently dropping a config.
   */
  public List<ConfigurationOption> resolve(List<ConfigurationOption> configurations) {
    List<ConfigurationOption> resolved = new ArrayList<>(configurations.size());
    for (ConfigurationOption configuration : configurations) {
      if (configuration.ref() != null) {
        ConfigurationOption definition = definitions.get(configuration.ref());
        if (definition == null) {
          throw new IllegalArgumentException(
              "Unknown shared configuration ref: '"
                  + configuration.ref()
                  + "'. Known ids: "
                  + definitions.keySet());
        }
        resolved.add(definition);
      } else {
        resolved.add(configuration);
      }
    }
    return resolved;
  }

  private static SharedConfigurationRegistry load() {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try (InputStream stream = SharedConfigurationRegistry.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing shared config definitions resource: " + RESOURCE);
      }
      RegistryFile file = mapper.readValue(stream, RegistryFile.class);
      Map<String, ConfigurationOption> definitions = withIds(file.configurations());
      Map<String, ConfigurationOption> globalConfigurations = withIds(file.globalConfigurations());
      // both sections share the definitions catalog of the generated instrumentation list
      for (String id : globalConfigurations.keySet()) {
        if (definitions.containsKey(id)) {
          throw new IllegalStateException(
              "Id '" + id + "' is used by both configurations and global_configurations");
        }
      }
      return new SharedConfigurationRegistry(definitions, globalConfigurations);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + RESOURCE, e);
    }
  }

  private static Map<String, ConfigurationOption> withIds(
      @Nullable Map<String, ConfigurationOption> configurations) {
    Map<String, ConfigurationOption> byId = new LinkedHashMap<>();
    if (configurations != null) {
      configurations.forEach((id, option) -> byId.put(id, option.withId(id)));
    }
    return byId;
  }

  private record RegistryFile(
      @Nullable Map<String, ConfigurationOption> configurations,
      @JsonProperty("global_configurations") @Nullable
          Map<String, ConfigurationOption> globalConfigurations) {}
}
