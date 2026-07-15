/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the shared configuration definitions ("globals") from {@code
 * shared-config-definitions.yaml} on the classpath and resolves {@code ref} entries in a module's
 * configuration list into full {@link ConfigurationOption}s.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SharedConfigurationRegistry {

  private static final String RESOURCE = "/shared-config-definitions.yaml";

  private static final SharedConfigurationRegistry INSTANCE =
      new SharedConfigurationRegistry(load());

  // id -> fully-specified option (with its id assigned)
  private final Map<String, ConfigurationOption> definitions;

  SharedConfigurationRegistry(Map<String, ConfigurationOption> definitions) {
    this.definitions = definitions;
  }

  public static SharedConfigurationRegistry getInstance() {
    return INSTANCE;
  }

  /** Returns the shared definitions keyed by id. */
  public Map<String, ConfigurationOption> definitions() {
    return definitions;
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

  private static Map<String, ConfigurationOption> load() {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try (InputStream stream = SharedConfigurationRegistry.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing shared config definitions resource: " + RESOURCE);
      }
      RegistryFile file = mapper.readValue(stream, RegistryFile.class);
      Map<String, ConfigurationOption> byId = new LinkedHashMap<>();
      Map<String, ConfigurationOption> configurations = file.configurations();
      if (configurations != null) {
        configurations.forEach((id, option) -> byId.put(id, option.withId(id)));
      }
      return byId;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + RESOURCE, e);
    }
  }

  private record RegistryFile(Map<String, ConfigurationOption> configurations) {}
}
