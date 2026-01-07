/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Objects.requireNonNull;

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
import org.springframework.core.env.SystemEnvironmentPropertySource;

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
    Map<String, Object> props = extractSpringProperties(environment);
    Map<String, Object> nested = convertFlatPropsToNested(props);
    return MAPPER.convertValue(nested, OpenTelemetryConfigurationModel.class);
  }

  private static String toEnvVarName(String propertyName) {
    return "OTEL_" + propertyName.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
  }

  private static Map<String, Object> extractSpringProperties(ConfigurableEnvironment environment) {
    MutablePropertySources propertySources = environment.getPropertySources();

    Map<String, Object> props = new HashMap<>();
    // iterate in reverse order to respect Spring property source precedence
    ArrayList<PropertySource<?>> sourcesList = new ArrayList<>();
    propertySources.forEach(sourcesList::add);
    for (int i = sourcesList.size() - 1; i >= 0; i--) {
      PropertySource<?> propertySource = sourcesList.get(i);
      if (propertySource instanceof EnumerablePropertySource<?>) {
        for (String propertyName :
            ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {

          String prefix = "otel.";
          if (propertySource instanceof SystemEnvironmentPropertySource) {
            prefix = "OTEL_";
          }

          if (propertyName.startsWith(prefix)) {
            Object property = propertySource.getProperty(propertyName);
            // Resolve ${} placeholders in String values while preserving types for others
            if (property instanceof String) {
              property = environment.resolvePlaceholders((String) property);
            }
            if (Objects.equals(property, "")) {
              property = null; // spring returns empty string for yaml null
            }

            if (propertySource instanceof SystemEnvironmentPropertySource) {
              if (property != null) {
                addEnvironmentVariableOverride(props, propertyName, property.toString());
              }
            } else {
              props.put(propertyName.substring(prefix.length()), property);
            }
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

  private static void addEnvironmentVariableOverride(
      Map<String, Object> props, String envVarName, String envVarValue) {
    // update the entry where toEnvVarName of propertyName is the key
    for (String key : props.keySet()) {
      String envVarName1 = fixEnvVarName(toEnvVarName(key));
      if (Objects.equals(envVarName, envVarName1)) {
        // preserve the original type if possible
        props.put(key, convertEnvVar(envVarValue, props, key));
        return;
      }
    }
  }

  private static String fixEnvVarName(String envVarName) {
    if (envVarName.contains("[")) {
      // fix override via env var or system property
      // see
      // https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding
      // For example, the configuration property my.service[0].other would use an
      // environment variable named MY_SERVICE_0_OTHER.
      return envVarName.replace("[", "_").replace("]", "").replace(".", "_");
    }
    return envVarName;
  }

  private static Object convertEnvVar(String property, Map<String, Object> props, String key) {
    Class<?> type = requireNonNull(props.get(key)).getClass();
    if (type == String.class) {
      return property;
    } else if (type == Integer.class) {
      return Integer.parseInt(property);
    } else if (type == Double.class) {
      return Double.parseDouble(property);
    } else if (type == Boolean.class) {
      return Boolean.parseBoolean(property);
    } else {
      throw new IllegalStateException("Unsupported property type: " + type + " for key: " + key);
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
