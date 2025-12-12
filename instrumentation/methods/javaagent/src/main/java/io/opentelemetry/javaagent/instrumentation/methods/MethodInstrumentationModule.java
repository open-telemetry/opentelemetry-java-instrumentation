/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@AutoService(InstrumentationModule.class)
public class MethodInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  private final List<TypeInstrumentation> typeInstrumentations;

  public MethodInstrumentationModule() {
    super("methods");
    typeInstrumentations = createInstrumentations();
  }

  private static List<TypeInstrumentation> createInstrumentations() {
    // First try structured declarative config (YAML format)
    Optional<List<TypeInstrumentation>> structured =
        DeclarativeConfigUtil.getStructuredList(
                GlobalOpenTelemetry.get(), "java", "methods", "include")
            .map(MethodsConfig::parseDeclarativeConfig);
    if (structured.isPresent()) {
      return structured.get();
    }

    // fall back to old string property format
    Optional<List<TypeInstrumentation>> legacy = parseConfigProperties();
    if (legacy.isPresent()) {
      return legacy.get();
    }

    // ensure at least one instrumentation for muzzle reference collection
    return singletonList(
        new MethodInstrumentation(null, singletonMap(SpanKind.INTERNAL, emptyList())));
  }

  private static Optional<List<TypeInstrumentation>> parseConfigProperties() {
    return DeclarativeConfigUtil.getString(GlobalOpenTelemetry.get(), "java", "methods", "include")
        .map(MethodsConfigurationParser::parse)
        .map(
            classMethodsToTrace ->
                classMethodsToTrace.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(
                        e ->
                            new MethodInstrumentation(
                                e.getKey(), singletonMap(SpanKind.INTERNAL, e.getValue())))
                    .collect(Collectors.toList()));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
