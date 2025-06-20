/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(InstrumentationModule.class)
public class MethodInstrumentationModule extends InstrumentationModule {

  private static final String TRACE_METHODS_CONFIG = "otel.instrumentation.methods.include";

  private final List<TypeInstrumentation> typeInstrumentations;

  public MethodInstrumentationModule() {
    super("methods");
    typeInstrumentations = createInstrumentations();
  }

  private static List<TypeInstrumentation> createInstrumentations() {
    DeclarativeConfigProperties methods =
        AgentInstrumentationConfig.get().getDeclarativeConfig("methods");
    List<TypeInstrumentation> list =
        methods != null ? MethodsConfig.parseDeclarativeConfig(methods) : parseConfigProperties();
    // ensure that there is at least one instrumentation so that muzzle reference collection could
    // work
    if (list.isEmpty()) {
      return singletonList(
          new MethodInstrumentation(null, singletonMap(SpanKind.INTERNAL, emptyList())));
    }
    return list;
  }

  private static List<TypeInstrumentation> parseConfigProperties() {
    Map<String, Set<String>> classMethodsToTrace =
        MethodsConfigurationParser.parse(
            AgentInstrumentationConfig.get().getString(TRACE_METHODS_CONFIG));

    return classMethodsToTrace.entrySet().stream()
        .filter(e -> !e.getValue().isEmpty())
        .map(
            e ->
                new MethodInstrumentation(
                    e.getKey(), singletonMap(SpanKind.INTERNAL, e.getValue())))
        .collect(Collectors.toList());
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }
}
