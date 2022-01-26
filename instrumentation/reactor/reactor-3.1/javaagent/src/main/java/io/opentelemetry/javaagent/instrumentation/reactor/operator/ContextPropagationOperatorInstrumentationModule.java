/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.operator;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ContextPropagationOperatorInstrumentationModule extends InstrumentationModule {

  public ContextPropagationOperatorInstrumentationModule() {
    super("reactor", "reactor-3.1", "reactor-context-propagation-operator");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("application.io.opentelemetry.context.Context");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextPropagationOperatorInstrumentation());
  }
}
