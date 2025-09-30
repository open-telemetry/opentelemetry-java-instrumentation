/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mojarra;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MojarraInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public MojarraInstrumentationModule() {
    super("jsf-mojarra", "jsf-mojarra-1.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ActionListenerImplInstrumentation(), new RestoreViewPhaseInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
