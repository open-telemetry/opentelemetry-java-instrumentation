/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumentationMetadataDeserializer
    extends JsonDeserializer<InstrumentationMetadata> {

  @Override
  public InstrumentationMetadata deserialize(JsonParser p, DeserializationContext ctx)
      throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    String description = textOrNull(node, "description");
    Boolean disabledByDefault = boolOrNull(node, "disabled_by_default");

    List<String> classifications = readClassifications(node.path("classification"));
    List<ConfigurationOption> configurations = readConfigurations(node.path("configurations"));

    return new InstrumentationMetadata(
        description, classifications, disabledByDefault, configurations);
  }

  @Nullable
  private static String textOrNull(JsonNode parent, String field) {
    JsonNode n = parent.path(field);
    return n.isMissingNode() || n.isNull() ? null : n.asText();
  }

  @Nullable
  private static Boolean boolOrNull(JsonNode parent, String field) {
    JsonNode n = parent.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asBoolean();
  }

  private static List<String> readClassifications(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return singletonList(InstrumentationClassification.LIBRARY.name());
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException("Classification must be an array");
    }
    List<String> result = new ArrayList<>();
    for (JsonNode c : node) {
      if (c.isTextual()) {
        result.add(c.asText());
      }
    }
    if (result.isEmpty()) {
      result.add(InstrumentationClassification.LIBRARY.name());
    }
    return unmodifiableList(result);
  }

  private static List<ConfigurationOption> readConfigurations(JsonNode configs) {
    if (!configs.isArray() || configs.size() == 0) {
      return emptyList();
    }
    List<ConfigurationOption> configurationOptions = new ArrayList<>(configs.size());
    for (JsonNode cfg : configs) {
      if (cfg.isNull() || !cfg.isObject()) {
        throw new IllegalArgumentException("Configuration entry must be an object");
      }

      String name = textOrNull(cfg, "name");
      if (name == null) {
        throw new IllegalArgumentException("Configuration entry is missing required 'name' field");
      }

      String desc =
          Objects.requireNonNull(
              textOrNull(cfg, "description"),
              "Configuration '" + name + "' is missing required 'description' field");
      String def =
          Objects.requireNonNull(
              textOrNull(cfg, "default"),
              "Configuration '" + name + "' is missing required 'default' field");
      String typeStr =
          Objects.requireNonNull(
              textOrNull(cfg, "type"),
              "Configuration '" + name + "' is missing required 'type' field");

      ConfigurationType type;
      try {
        type = ConfigurationType.from(typeStr);
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
            "Configuration '" + name + "' has invalid type: '" + typeStr + "'", ex);
      }
      configurationOptions.add(new ConfigurationOption(name, desc, def, type));
    }
    return unmodifiableList(configurationOptions);
  }
}
