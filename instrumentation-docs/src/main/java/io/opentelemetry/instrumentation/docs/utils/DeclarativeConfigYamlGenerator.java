/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
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

/**
 * Generates a declarative configuration YAML file showing all available instrumentation
 * configurations.
 */
public class DeclarativeConfigYamlGenerator {

  /** Maximum line length for YAML output including indentation. */
  private static final int MAX_LINE_LENGTH = 100;

  /** Wrapper class to hold both a value and its description for YAML output. */
  private record ConfigValue(Object value, String description) {}

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
        String flatName = config.name();

        // Skip duplicates
        if (seenConfigs.contains(flatName)) {
          continue;
        }
        seenConfigs.add(flatName);

        String declarativePath = DeclarativeConfigConverter.toDeclarativePath(flatName);

        insertIntoTree(tree, declarativePath, config);
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
    ConfigValue configValue = new ConfigValue(value, config.description());
    current.put(lastPart, configValue);
  }

  /**
   * Converts a configuration's default value to the appropriate type.
   *
   * @param config the configuration option
   * @return the converted value
   */
  private static Object convertValue(ConfigurationOption config) {
    String defaultValue = config.defaultValue();
    ConfigurationType type = config.type();

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
  @SuppressWarnings("unchecked")
  private static void writeYaml(Map<String, Object> tree, BufferedWriter writer, int indent)
      throws IOException {

    List<String> keys = new ArrayList<>(tree.keySet());
    Collections.sort(keys);

    boolean first = true;
    for (String key : keys) {
      Object value = tree.get(key);

      // Extract ConfigValue if present
      String description = null;
      Object actualValue = value;
      if (value instanceof ConfigValue configValue) {
        description = configValue.description;
        actualValue = configValue.value;
      }

      // Write description as comment if present
      if (description != null && !description.isEmpty()) {
        // Add blank line before comment (except for first entry)
        if (!first) {
          writer.write("\n");
        }
        String[] descLines = description.split("\n");
        for (String line : descLines) {
          List<String> wrappedLines = wrapText(line.trim(), indent);
          for (String wrappedLine : wrappedLines) {
            writer.write("  ".repeat(indent));
            writer.write("# ");
            writer.write(wrappedLine);
            writer.write("\n");
          }
        }
      }
      first = false;

      // Handle /development suffix in key
      String yamlKey = key;
      if (key.contains("/development")) {
        yamlKey = key.replace("/development", "") + "/development";
      }

      // indentation
      writer.write("  ".repeat(indent));
      writer.write(yamlKey);
      writer.write(":");

      if (actualValue instanceof Map) {
        writer.write("\n");
        writeYaml((Map<String, Object>) actualValue, writer, indent + 1);
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
      // Quote strings if they're empty or contain special characters
      if (str.isEmpty() || str.contains(":") || str.contains("#") || str.contains("\"")) {
        return "\"" + str.replace("\"", "\\\"") + "\"";
      }
      return str;
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
