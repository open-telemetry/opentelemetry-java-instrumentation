/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.Arrays;
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

    Map<String, Set<String>> classMethodsToTrace =
        MethodsConfigurationParser.parse(
            AgentInstrumentationConfig.get().getString(TRACE_METHODS_CONFIG));

    typeInstrumentations =
        classMethodsToTrace.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(e -> new MethodInstrumentation(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
  }

  // the default configuration has empty "otel.instrumentation.methods.include", and so doesn't
  // generate any TypeInstrumentation for muzzle to analyze
  @Override
  public List<String> getAdditionalHelperClassNames() {
    return typeInstrumentations.isEmpty()
        ? emptyList()
        : Arrays.asList(
            "io.opentelemetry.javaagent.instrumentation.methods.MethodSingletons",
            "io.opentelemetry.javaagent.instrumentation.methods.MethodSingletons$BootstrapLoader");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }
}
