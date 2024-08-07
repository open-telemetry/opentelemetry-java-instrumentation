/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_4.operator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ContextPropagationOperator34InstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ContextPropagationOperator34InstrumentationModule() {
    super("reactor", "reactor-3.4", "reactor-context-propagation-operator");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "application.io.opentelemetry.context.Context", "reactor.util.context.ContextView");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextPropagationOperator34Instrumentation());
  }

  @Override
  public String getModuleGroup() {
    // This module uses the api context bridge helpers, therefore must be in the same classloader
    return "opentelemetry-api-bridge";
  }
}
