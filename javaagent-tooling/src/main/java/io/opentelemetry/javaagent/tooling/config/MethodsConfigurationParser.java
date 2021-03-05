/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MethodsConfigurationParser {

  private static final Logger log = LoggerFactory.getLogger(MethodsConfigurationParser.class);

  static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.$]+";
  private static final String METHOD_LIST_REGEX = "\\s*(?:\\w+\\s*,)*\\s*(?:\\w+\\s*,?)\\s*";
  private static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "]\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "]";

  /**
   * This method takes a string in a form of {@code
   * "io.package.ClassName[method1,method2];my.example[someMethodName];"} and returns a map where
   * keys are class names and corresponding value is a set of methods for that class.
   *
   * <p>Strings of such format are used e.g. to configure {@code TraceConfigInstrumentation}
   */
  public static Map<String, Set<String>> parse(String configString) {
    if (configString == null || configString.trim().isEmpty()) {
      return Collections.emptyMap();
    } else if (!validateConfigString(configString)) {
      log.warn(
          "Invalid trace method config '{}'. Must match 'package.Class$Name[method1,method2];*'.",
          configString);
      return Collections.emptyMap();
    } else {
      Map<String, Set<String>> toTrace = new HashMap<>();
      String[] classMethods = configString.split(";", -1);
      for (String classMethod : classMethods) {
        if (classMethod.trim().isEmpty()) {
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
