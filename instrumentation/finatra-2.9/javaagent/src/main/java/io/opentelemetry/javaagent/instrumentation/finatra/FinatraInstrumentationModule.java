/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class FinatraInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public FinatraInstrumentationModule() {
    super("finatra", "finatra-2.9");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new FinatraRouteInstrumentation(),
        new FinatraRouteBuilderInstrumentation(),
        new FinatraExceptionManagerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
