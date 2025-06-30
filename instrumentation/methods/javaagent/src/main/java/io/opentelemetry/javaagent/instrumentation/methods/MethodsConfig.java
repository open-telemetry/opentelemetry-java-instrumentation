/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodsConfig {

  private static final Logger logger = Logger.getLogger(MethodsConfig.class.getName());

  private MethodsConfig() {}

  static List<TypeInstrumentation> parseDeclarativeConfig(DeclarativeConfigProperties methods) {
    return methods.getStructuredList("include", emptyList()).stream()
        .flatMap(MethodsConfig::parseMethodInstrumentation)
        .collect(Collectors.toList());
  }

  private static Stream<MethodInstrumentation> parseMethodInstrumentation(
      DeclarativeConfigProperties config) {
    String clazz = config.getString("class");
    if (isNullOrEmpty(clazz)) {
      logger.log(Level.WARNING, "Invalid methods configuration - class name missing: {0}", config);
      return Stream.empty();
    }

    Map<SpanKind, Collection<String>> methodNames = new EnumMap<>(SpanKind.class);
    for (DeclarativeConfigProperties method : config.getStructuredList("methods", emptyList())) {
      String methodName = method.getString("name");
      if (isNullOrEmpty(methodName)) {
        logger.log(
            Level.WARNING, "Invalid methods configuration - method name missing: {0}", method);
        continue;
      }
      String spanKind = method.getString("span_kind", "INTERNAL");
      try {
        methodNames
            .computeIfAbsent(
                SpanKind.valueOf(spanKind.toUpperCase(Locale.ROOT)), unused -> new ArrayList<>())
            .add(methodName);
      } catch (IllegalArgumentException e) {
        logger.log(
            Level.WARNING,
            "Invalid methods configuration - unknown span_kind: {0} for method: {1}",
            new Object[] {spanKind, methodName});
      }
    }

    if (methodNames.isEmpty()) {
      logger.log(Level.WARNING, "Invalid methods configuration - no methods defined: {0}", config);
      return Stream.empty();
    }

    return Stream.of(new MethodInstrumentation(clazz, methodNames));
  }

  private static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
