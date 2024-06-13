/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReactorInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ReactorInstrumentationModule() {
    super("reactor", "reactor-3.1");
  }

  @Override
  public String getModuleGroup() {
    // needs to be in the same module as ContextPropagationOperatorInstrumentation
    return "opentelemetry-api-bridge";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HooksInstrumentation());
  }
}
