/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.DeclarativeSchema;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * Generates a declarative configuration YAML file showing all available instrumentation
 * configurations.
 */
public class DeclarativeConfigYamlGenerator {

  /** Maximum line length for YAML output including indentation. */
  private static final int MAX_LINE_LENGTH = 100;

  /**
   * Wrapper class to hold a value, its description, and (for structured lists) the schema of a
   * single list entry, for YAML output.
   */
  private record ConfigValue(
      Object value, String description, @Nullable DeclarativeSchema schema) {}

  /** Used to render individual scalar values so they are always valid YAML. */
  private static final Yaml SCALAR_YAML = new Yaml();

  /**
   * Generates a declarative configuration YAML file from instrumentation modules.
   *
   * @param modules the list of instrumentation modules
   * @param writer the writer to output the YAML to
   * @throws IOException if an I/O error occurs
   */
  public static void generateConfigurationYaml(
      List<InstrumentationModule> modules, BufferedWriter writer) throws IOException {

    Map<String, Object> configTree = buildConfigTree(modules);

    writeYaml(configTree, writer, 0);
  }

  /**
   * Builds a nested tree structure from all configurations.
   *
   * @param modules the list of instrumentation modules
   * @return the configuration tree
   */
  private static Map<String, Object> buildConfigTree(List<InstrumentationModule> modules) {
    Map<String, Object> tree = new TreeMap<>();
    Set<String> seenConfigs = new HashSet<>();

    for (InstrumentationModule module : modules) {
      List<ConfigurationOption> configs = module.getMetadata().getConfigurations();

      for (ConfigurationOption config : configs) {
        String declarativeName = config.declarativeName();

        // Skip configurations that don't declare a declarative config name.
        if (declarativeName == null || declarativeName.isBlank()) {
          continue;
        }

        // Skip duplicates (e.g. common configurations shared across many modules).
        if (!seenConfigs.add(declarativeName)) {
          continue;
        }

        // declarative_name is relative to the "instrumentation" config node (e.g.
        // "java.grpc.emit_message_events" or "general.http.client.request_captured_headers"),
        // so nest it under "instrumentation" to form a complete declarative config path.
        insertIntoTree(tree, "instrumentation/development." + declarativeName, config);
      }
    }

    return tree;
  }

  /**
   * Inserts a configuration into the tree structure.
   *
   * @param tree the tree to insert into
   * @param path the declarative path (e.g., "instrumentation.java.grpc.emit_message_events")
   * @param config the configuration option
   */
  @SuppressWarnings("unchecked")
  private static void insertIntoTree(
      Map<String, Object> tree, String path, ConfigurationOption config) {

    // Split path by dots (but not /development suffix)
    String[] parts = path.split("\\.");

    // Filter out empty parts to avoid empty keys in YAML
    List<String> filteredParts = new ArrayList<>();
    for (String part : parts) {
      if (!part.isEmpty()) {
        filteredParts.add(part);
      }
    }

    if (filteredParts.isEmpty()) {
      return;
    }

    Map<String, Object> current = tree;

    // Navigate/create tree structure up to the last part
    for (int i = 0; i < filteredParts.size() - 1; i++) {
      String part = filteredParts.get(i);
      current.putIfAbsent(part, new TreeMap<String, Object>());
      Object next = current.get(part);
      if (!(next instanceof Map)) {
        // This shouldn't happen, but handle it
        Map<String, Object> newMap = new TreeMap<>();
        current.put(part, newMap);
        current = newMap;
      } else {
        current = (Map<String, Object>) next;
      }
    }

    // Insert the final key with its value and description
    String lastPart = filteredParts.get(filteredParts.size() - 1);
    Object value = convertValue(config);
    ConfigValue configValue =
        new ConfigValue(
            value,
            config.description(),
            isStructuredList(config) ? config.declarativeSchema() : null);
    current.put(lastPart, configValue);
  }

  /**
   * Returns whether the declarative form of a configuration is a list of objects. The flat {@code
   * type} describes the system property (e.g. {@code map} for the {@code host=service} peer service
   * mapping), so the declarative shape can differ and is then marked by {@code declarative_type}.
   *
   * @param config the configuration option
   * @return true if the declarative form is a structured list
   */
  private static boolean isStructuredList(ConfigurationOption config) {
    return config.type() == ConfigurationType.STRUCTURED_LIST
        || config.declarativeType() == ConfigurationType.STRUCTURED_LIST;
  }

  /**
   * Converts a configuration's default value to the appropriate type.
   *
   * @param config the configuration option
   * @return the converted value
   */
  private static Object convertValue(ConfigurationOption config) {
    String defaultValue = config.defaultValue();
    // the flat type describes the system property, so prefer declarative_type when the declarative
    // form has a different shape (e.g. a comma-separated string for a flat list property)
    ConfigurationType type =
        config.declarativeType() != null ? config.declarativeType() : config.type();

    // A structured list always defaults to an empty list; the shape of an entry is documented in
    // the comment block written above the key rather than emitted as (non-default) sample config.
    if (isStructuredList(config)) {
      return emptyList();
    }

    if (defaultValue == null || defaultValue.isEmpty() || defaultValue.equals("null")) {
      return switch (type) {
        case BOOLEAN -> false;
        case INT -> 0;
        case LIST -> emptyList();
        case MAP -> emptyMap();
        default -> "";
      };
    }

    return switch (type) {
      case BOOLEAN -> Boolean.parseBoolean(defaultValue);
      case INT -> {
        try {
          yield Integer.parseInt(defaultValue);
        } catch (NumberFormatException e) {
          yield 0;
        }
      }
      case LIST -> parseList(defaultValue);
      case MAP -> emptyMap();
      default -> defaultValue; // STRING
    };
  }

  /**
   * Parses a list from a string representation.
   *
   * @param value the string value
   * @return the parsed list
   */
  private static List<String> parseList(String value) {
    if (value.isEmpty() || value.equals("[]")) {
      return emptyList();
    }

    // Simple parsing - split by comma
    String trimmed = value.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }

    if (trimmed.isEmpty()) {
      return emptyList();
    }

    String[] parts = trimmed.split(",");
    List<String> result = new ArrayList<>();
    for (String part : parts) {
      result.add(part.trim());
    }
    return result;
  }

  /**
   * Writes the configuration tree as YAML.
   *
   * @param tree the configuration tree
   * @param writer the writer to output to
   * @param indent the current indentation level
   * @throws IOException if an I/O error occurs
   */
  private static void writeYaml(Map<String, Object> tree, BufferedWriter writer, int indent)
      throws IOException {

    List<String> keys = new ArrayList<>(tree.keySet());
    Collections.sort(keys);

    boolean first = true;
    for (String key : keys) {
      Object value = tree.get(key);

      // Extract ConfigValue if present
      String description = null;
      DeclarativeSchema schema = null;
      Object actualValue = value;
      if (value instanceof ConfigValue configValue) {
        description = configValue.description();
        schema = configValue.schema();
        actualValue = configValue.value();
      }

      List<String> commentLines = new ArrayList<>();
      if (description != null && !description.isEmpty()) {
        for (String line : description.split("\n")) {
          commentLines.addAll(wrapText(line.trim(), indent));
        }
      }
      if (schema != null && !schema.properties().isEmpty()) {
        commentLines.addAll(schemaCommentLines(key, schema, indent));
      }

      if (!commentLines.isEmpty()) {
        // Add blank line before comment (except for first entry)
        if (!first) {
          writer.write("\n");
        }
        for (String commentLine : commentLines) {
          writer.write("  ".repeat(indent));
          writer.write("# ");
          writer.write(commentLine);
          writer.write("\n");
        }
      }
      first = false;

      // Write key with indentation
      writer.write("  ".repeat(indent));
      writer.write(key);
      writer.write(":");

      if (actualValue instanceof Map) {
        // Safe to cast - the tree is built with Map<String, Object> structure
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) actualValue;
        if (map.isEmpty()) {
          writer.write(" {}\n");
        } else {
          writer.write("\n");
          writeYaml(map, writer, indent + 1);
        }
      } else if (actualValue instanceof List<?> list) {
        if (list.isEmpty()) {
          writer.write(" []\n");
        } else {
          writer.write("\n");
          for (Object item : list) {
            writer.write("  ".repeat(indent + 1));
            writer.write("- ");
            writer.write(formatValue(item));
            writer.write("\n");
          }
        }
      } else {
        writer.write(" ");
        writer.write(formatValue(actualValue));
        writer.write("\n");
      }
    }
  }

  /**
   * Documents the per-entry object shape of a structured list, since the emitted value is only the
   * empty-list default. Produces the property list followed by a sample entry, e.g.
   *
   * <pre>
   * Each list entry is an object with the following properties:
   *   peer (string, required): Host name or IP address to match against.
   *   service_name (string, required): Peer service name to record for matching peers.
   * Example:
   *   service_peer_mapping:
   *     - peer: host
   *       service_name: serviceName
   * </pre>
   *
   * @param key the configuration key the schema belongs to
   * @param schema the schema of a single list entry
   * @param indent the current indentation level
   * @return the comment lines, without their leading "# "
   */
  private static List<String> schemaCommentLines(String key, DeclarativeSchema schema, int indent) {

    List<String> lines = new ArrayList<>();
    Set<String> required =
        schema.required() == null ? emptySet() : new HashSet<>(schema.required());

    lines.add("Each list entry is an object with the following properties:");
    schema
        .properties()
        .forEach(
            (name, property) -> {
              StringBuilder line = new StringBuilder(name).append(" (").append(property.type());
              if (required.contains(name)) {
                line.append(", required");
              }
              line.append(")");
              if (property.description() != null && !property.description().isBlank()) {
                line.append(": ").append(property.description().trim());
              }
              // Indent the property under the introduction line, hanging-indenting any
              // continuation lines so they don't read as separate properties.
              List<String> wrapped = wrapText(line.toString(), indent + 2);
              for (int i = 0; i < wrapped.size(); i++) {
                lines.add((i == 0 ? "  " : "    ") + wrapped.get(i));
              }
            });

    lines.add("Example:");
    lines.add("  " + key + ":");
    boolean firstProperty = true;
    for (Map.Entry<String, DeclarativeSchema.Property> entry : schema.properties().entrySet()) {
      // The first property carries the "- " sequence indicator; the rest align beneath it.
      lines.add(
          (firstProperty ? "    - " : "      ")
              + entry.getKey()
              + ": "
              + formatValue(exampleValue(entry.getKey(), entry.getValue())));
      firstProperty = false;
    }

    return lines;
  }

  /**
   * Returns the sample value to show for a schema property: its {@code example} when the metadata
   * provides one, otherwise its {@code default}, otherwise a placeholder built from its name.
   *
   * @param name the property name
   * @param property the property
   * @return the sample value
   */
  private static Object exampleValue(String name, DeclarativeSchema.Property property) {
    if (property.example() != null) {
      return property.example();
    }
    if (property.defaultValue() != null) {
      return property.defaultValue();
    }
    return "<" + name + ">";
  }

  /**
   * Wraps text to fit within the maximum line length.
   *
   * @param text the text to wrap
   * @param indent the indentation level
   * @return a list of wrapped lines
   */
  private static List<String> wrapText(String text, int indent) {
    List<String> lines = new ArrayList<>();

    // Calculate available width: MAX_LINE_LENGTH - (indent * 2 spaces) - "# "
    int indentChars = indent * 2;
    int prefixChars = 2; // "# "
    int availableWidth = MAX_LINE_LENGTH - indentChars - prefixChars;

    // If available width is too small, use minimum of 40 characters
    if (availableWidth < 40) {
      availableWidth = 40;
    }

    // If text fits within available width, return as-is
    if (text.length() <= availableWidth) {
      lines.add(text);
      return lines;
    }

    // Wrap at word boundaries
    String[] words = text.split("\\s+");
    StringBuilder currentLine = new StringBuilder();

    for (String word : words) {
      // If adding this word would exceed the limit
      if (currentLine.length() + word.length() + 1 > availableWidth) {
        // If current line is not empty, save it and start a new line
        if (!currentLine.isEmpty()) {
          lines.add(currentLine.toString());
          currentLine = new StringBuilder(word);
        } else {
          lines.add(word);
        }
      } else {
        if (!currentLine.isEmpty()) {
          currentLine.append(" ");
        }
        currentLine.append(word);
      }
    }

    // Add remaining text
    if (!currentLine.isEmpty()) {
      lines.add(currentLine.toString());
    }

    return lines;
  }

  /**
   * Formats a value for YAML output.
   *
   * @param value the value to format
   * @return the formatted string
   */
  private static String formatValue(@Nullable Object value) {
    if (value == null) {
      return "\"\"";
    }
    if (value instanceof String str) {
      // Delegate to SnakeYAML so scalars that would otherwise be misinterpreted (e.g. "*",
      // "true", "null", leading "- ") are quoted, guaranteeing the output is valid YAML.
      return SCALAR_YAML.dump(str).strip();
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
      return "{}";
    }
    return String.valueOf(value);
  }

  private DeclarativeConfigYamlGenerator() {}
}
