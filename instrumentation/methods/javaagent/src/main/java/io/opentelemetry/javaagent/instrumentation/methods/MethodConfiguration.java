/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodConfiguration {

  private static final Logger logger = Logger.getLogger(MethodConfiguration.class.getName());

  private final List<TypeInstrumentation> typeInstrumentations;

  MethodConfiguration(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties javaConfig = empty();
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      DeclarativeConfigProperties instrumentationConfig =
          extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
      if (instrumentationConfig != null) {
        javaConfig = instrumentationConfig.getStructured("java", empty());
      }
    }
    DeclarativeConfigProperties methodsConfig = javaConfig.getStructured("methods", empty());

    this.typeInstrumentations = parse(methodsConfig);
  }

  List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }

  private static List<TypeInstrumentation> parse(DeclarativeConfigProperties methods) {
    // First try structured declarative config (YAML format)
    List<DeclarativeConfigProperties> includeList = methods.getStructuredList("include");
    if (includeList != null) {
      return includeList.stream()
          .flatMap(MethodConfiguration::parseMethodInstrumentation)
          .collect(Collectors.toList());
    }

    // Fall back to old string property format
    String include = methods.getString("include");
    if (include != null) {
      return parseConfigString(include);
    }

    return emptyList();
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

  private static List<TypeInstrumentation> parseConfigString(String include) {
    Map<String, Set<String>> classMethodsToTrace = MethodsConfigurationParser.parse(include);
    return classMethodsToTrace.entrySet().stream()
        .filter(e -> !e.getValue().isEmpty())
        .map(
            e ->
                new MethodInstrumentation(
                    e.getKey(), singletonMap(SpanKind.INTERNAL, e.getValue())))
        .collect(Collectors.toList());
  }
}
