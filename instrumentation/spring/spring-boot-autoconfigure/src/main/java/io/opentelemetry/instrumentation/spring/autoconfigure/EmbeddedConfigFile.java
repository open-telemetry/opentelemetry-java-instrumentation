/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

class EmbeddedConfigFile {

  static final Pattern ARRAY_PATTERN = Pattern.compile("(.+)\\[(\\d+)\\]");

  private EmbeddedConfigFile() {
    // Utility class
  }

  static OpenTelemetryConfigurationModel extractModel(ConfigurableEnvironment environment) {
    MutablePropertySources propertySources = environment.getPropertySources();

    Map<String, Object> props = new HashMap<>();
    for (PropertySource<?> propertySource : propertySources) {
      if (propertySource instanceof EnumerablePropertySource<?>) {
        for (String propertyName :
            ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
          if (propertyName.startsWith("otel.")) {
            String property = environment.getProperty(propertyName);
            if (Objects.equals(property, "")) {
              property = null; // spring returns empty string for yaml null
            }
            props.put(
                propertyName.substring("otel.".length()), property);
          }
        }
      }
    }

    Map<String, Object> nestedProps = convertFlatPropsToNested(props);
    if (nestedProps.isEmpty()) {
      throw new IllegalStateException("No application.yaml file found.");
    } else {
      String string = new Dump(DumpSettings.builder().build()).dumpToString(nestedProps);
      return DeclarativeConfiguration.parse(
          new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
    }
  }

  /**
   * Convert flat property map to nested structure. e.g. "otel.instrumentation.java.list[0]" = "one"
   * and "otel.instrumentation.java.list[1]" = "two" becomes: {otel: {instrumentation: {java: {list:
   * ["one", "two"]}}}}
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> convertFlatPropsToNested(Map<String, Object> flatProps) {
    Map<String, Object> result = new HashMap<>();

    for (Map.Entry<String, Object> entry : flatProps.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // Split the key by dots
      String[] parts = key.split("\\.");
      Map<String, Object> current = result;

      for (int i = 0; i < parts.length; i++) {
        String part = parts[i];
        boolean isLast = (i == parts.length - 1);

        // Check if this part contains an array index like "list[0]"
        Matcher matcher = ARRAY_PATTERN.matcher(part);
        if (matcher.matches()) {
          String arrayName = matcher.group(1);
          int index = Integer.parseInt(matcher.group(2));

          // Get or create the list
          if (!current.containsKey(arrayName)) {
            current.put(arrayName, new ArrayList<>());
          }
          List<Object> list = (List<Object>) current.get(arrayName);

          // Ensure the list is large enough
          while (list.size() <= index) {
            list.add(null);
          }

          if (isLast) {
            list.set(index, value);
          } else {
            // Need to create a nested map at this index
            if (list.get(index) == null) {
              list.set(index, new HashMap<String, Object>());
            }
            current = (Map<String, Object>) list.get(index);
          }
        } else {
          // Regular property (not an array)
          if (isLast) {
            current.put(part, value);
          } else {
            // Need to create a nested map
            if (!current.containsKey(part)) {
              current.put(part, new HashMap<String, Object>());
            }
            current = (Map<String, Object>) current.get(part);
          }
        }
      }
    }

    return result;
  }
}
