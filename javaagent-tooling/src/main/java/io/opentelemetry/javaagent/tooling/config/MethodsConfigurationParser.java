/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class MethodsConfigurationParser {

  private static final Logger logger = Logger.getLogger(MethodsConfigurationParser.class.getName());

  private static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.$]+";
  private static final String METHOD_LIST_REGEX = "(?:\\s*\\w+\\s*,)*+(?:\\s*\\w+)?\\s*";
  private static final String CONFIG_FORMAT =
      PACKAGE_CLASS_NAME_REGEX + "(?:\\[" + METHOD_LIST_REGEX + "])?";

  /**
   * Parse exclude methods configuration from declarative config.
   *
   * <p>First tries structured declarative config (YAML format), then falls back to old string/list
   * property format for backward compatibility.
   *
   * <p>Example YAML structure:
   *
   * <pre>{@code
   * exclude_methods:
   *   - class: com.example.MyClass
   *     methods: [method1, method2]
   *   - class: com.example.AnotherClass
   *     methods: [someMethod]
   * }</pre>
   */
  public static Map<String, Set<String>> parseExcludeMethods(
      ExtendedDeclarativeConfigProperties config) {
    // First try structured declarative config (YAML format)
    List<DeclarativeConfigProperties> excludeList = config.getStructuredList("exclude_methods");
    if (excludeList != null) {
      return excludeList.stream()
          .filter(
              entry -> {
                String className = entry.getString("class");
                return className != null && !className.isEmpty();
              })
          .collect(
              Collectors.toMap(
                  entry -> entry.getString("class"),
                  entry ->
                      new HashSet<>(entry.getScalarList("methods", String.class, emptyList()))));
    }

    // Fall back to old string property format for backward compatibility
    String excludeMethodsString = config.getString("exclude_methods");
    if (excludeMethodsString != null) {
      return parse(excludeMethodsString);
    }

    return emptyMap();
  }

  /**
   * This method takes a string in a form of {@code
   * "io.package.ClassName[method1,method2];my.example[someMethodName];"} and returns a map where
   * keys are class names and corresponding value is a set of methods for that class.
   *
   * <p>Strings of such format are used e.g. to configure {@code TraceConfigInstrumentation}
   */
  public static Map<String, Set<String>> parse(String configString) {
    if (configString == null || configString.trim().isEmpty()) {
      return emptyMap();
    } else if (!validateConfigString(configString)) {
      logger.log(
          WARNING,
          "Invalid trace method config \"{0}\". Must match 'package.Class$Name[method1,method2];*'.",
          configString);
      return emptyMap();
    } else {
      Map<String, Set<String>> toTrace = new HashMap<>();
      String[] classMethods = configString.split(";", -1);
      for (String classMethod : classMethods) {
        if (classMethod.trim().isEmpty()) {
          continue;
        }
        if (!classMethod.contains("[")) {
          toTrace.put(classMethod.trim(), Collections.emptySet());
          continue;
        }
        String[] splitClassMethod = classMethod.split("\\[", -1);
        String className = splitClassMethod[0];
        String method = splitClassMethod[1].trim();
        String methodNames = method.substring(0, method.length() - 1);
        String[] splitMethodNames = methodNames.split(",", -1);
        Set<String> trimmedMethodNames = new HashSet<>(splitMethodNames.length);
        for (String methodName : splitMethodNames) {
          String trimmedMethodName = methodName.trim();
          if (!trimmedMethodName.isEmpty()) {
            trimmedMethodNames.add(trimmedMethodName);
          }
        }
        if (!trimmedMethodNames.isEmpty()) {
          toTrace.put(className.trim(), trimmedMethodNames);
        }
      }
      return toTrace;
    }
  }

  private static boolean validateConfigString(String configString) {
    for (String segment : configString.split(";")) {
      if (!segment.trim().matches(CONFIG_FORMAT)) {
        return false;
      }
    }
    return true;
  }

  private MethodsConfigurationParser() {}
}
