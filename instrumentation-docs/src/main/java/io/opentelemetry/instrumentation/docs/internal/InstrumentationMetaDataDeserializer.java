/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumentationMetaDataDeserializer
    extends JsonDeserializer<InstrumentationMetaData> {

  @Override
  public InstrumentationMetaData deserialize(JsonParser p, DeserializationContext ctx)
      throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    String description = textOrNull(node, "description");
    Boolean disabledByDefault = boolOrNull(node, "disabled_by_default");

    List<String> classifications = readClassifications(node.path("classification"));
    List<ConfigurationOption> configurations = readConfigurations(node.path("configurations"));

    return new InstrumentationMetaData(
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
      return List.of(InstrumentationClassification.LIBRARY.name());
    }
    List<String> out = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode c : node) {
        if (c.isTextual()) {
          out.add(c.asText());
        }
      }
    } else if (node.isTextual()) {
      out.add(node.asText());
    }
    if (out.isEmpty()) {
      out.add(InstrumentationClassification.LIBRARY.name());
    }
    return unmodifiableList(out);
  }

  private static List<ConfigurationOption> readConfigurations(JsonNode configs) {
    if (!configs.isArray()) {
      return emptyList();
    }
    List<ConfigurationOption> out = new ArrayList<>(configs.size());
    int i = 0;
    for (JsonNode cfg : configs) {
      String name = Objects.requireNonNull(textOrNull(cfg, "name"));
      String desc = Objects.requireNonNull(textOrNull(cfg, "description"));
      String def = Objects.requireNonNull(textOrNull(cfg, "default"));
      String typeStr = Objects.requireNonNull(textOrNull(cfg, "type"));

      ConfigurationType type;
      try {
        type = ConfigurationType.valueOf(typeStr.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
            "configurations[" + i + "].type invalid: '" + typeStr + "'", ex);
      }
      out.add(new ConfigurationOption(name, desc, def, type));
      i++;
    }
    return unmodifiableList(out);
  }
}
