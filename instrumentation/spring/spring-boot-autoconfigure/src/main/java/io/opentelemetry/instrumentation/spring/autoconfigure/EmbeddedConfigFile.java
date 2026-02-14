/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

class EmbeddedConfigFile {

  private static final Pattern ARRAY_PATTERN = Pattern.compile("(.+)\\[(\\d+)]$");

  // the entire configuration is copied from
  // https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/incubator/src/main/java/io/opentelemetry/sdk/extension/incubator/fileconfig/DeclarativeConfiguration.java#L66-L79
  // which is not public
  private static final ObjectMapper MAPPER;

  static {
    MAPPER =
        new ObjectMapper()
            // Create empty object instances for keys which are present but have null values
            .setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    // Boxed primitives which are present but have null values should be set to null, rather than
    // empty instances
    MAPPER.configOverride(String.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Integer.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Double.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Boolean.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
  }

  private EmbeddedConfigFile() {}

  static OpenTelemetryConfigurationModel extractModel(ConfigurableEnvironment environment) {
    Map<String, String> props = extractSpringProperties(environment);
    return convertToOpenTelemetryConfigurationModel(props);
  }

  private static Map<String, String> extractSpringProperties(ConfigurableEnvironment environment) {
    MutablePropertySources propertySources = environment.getPropertySources();

    Map<String, String> props = new HashMap<>();
    for (PropertySource<?> propertySource : propertySources) {
      if (propertySource instanceof EnumerablePropertySource<?>) {
        for (String propertyName :
            ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
          if (propertyName.startsWith("otel.")) {
            String property = environment.getProperty(propertyName);
            if (Objects.equals(property, "")) {
              property = null; // spring returns empty string for yaml null
            }
            if (propertyName.contains("[")) {
              // fix override via env var or system property
              // see
              // https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding
              // For example, the configuration property my.service[0].other would use an
              // environment variable named MY_SERVICE_0_OTHER.
              String envVarName =
                  propertyName
                      .replace("[", "_")
                      .replace("]", "")
                      .replace(".", "_")
                      .toUpperCase(Locale.ROOT);
              String envVarValue = environment.getProperty(envVarName);
              if (envVarValue != null) {
                property = envVarValue;
              }
            }

            props.put(propertyName.substring("otel.".length()), property);
          }
        }
      }
    }

    if (props.isEmpty()) {
      throw new IllegalStateException(
          "No properties found with prefix 'otel.' - this should not happen, because we checked "
              + "'environment.getProperty(\"otel.file_format\", String.class) != null' earlier");
    }
    return props;
  }

  static OpenTelemetryConfigurationModel convertToOpenTelemetryConfigurationModel(
      Map<String, String> flatProps) {
    Map<String, Object> nested = convertFlatPropsToNested(flatProps);

    return getObjectMapper().convertValue(nested, OpenTelemetryConfigurationModel.class);
  }

  static ObjectMapper getObjectMapper() {
    return MAPPER;
  }

  /**
   * Convert flat property map to nested structure. e.g. "otel.instrumentation.java.list[0]" = "one"
   * and "otel.instrumentation.java.list[1]" = "two" becomes: {otel: {instrumentation: {java: {list:
   * ["one", "two"]}}}}
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> convertFlatPropsToNested(Map<String, String> flatProps) {
    Map<String, Object> result = new HashMap<>();

    for (Map.Entry<String, String> entry : flatProps.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

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

          ArrayList<Object> list =
              (ArrayList<Object>) current.computeIfAbsent(arrayName, k -> new ArrayList<>());

          // Ensure the list is large enough
          list.ensureCapacity(index + 1);
          while (list.size() <= index) {
            list.add(null);
          }

          if (isLast) {
            list.set(index, value);
          } else {
            // Need to create a nested map at this index
            current = (Map<String, Object>) list.get(index);
            if (current == null) {
              current = new HashMap<>();
              list.set(index, current);
            }
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
