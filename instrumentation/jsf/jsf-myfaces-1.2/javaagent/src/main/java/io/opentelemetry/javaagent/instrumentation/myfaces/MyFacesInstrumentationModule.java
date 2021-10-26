/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MyFacesInstrumentationModule extends InstrumentationModule {
  public MyFacesInstrumentationModule() {
    super("myfaces", "myfaces-1.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ActionListenerImplInstrumentation(), new RestoreViewExecutorInstrumentation());
  }
}
