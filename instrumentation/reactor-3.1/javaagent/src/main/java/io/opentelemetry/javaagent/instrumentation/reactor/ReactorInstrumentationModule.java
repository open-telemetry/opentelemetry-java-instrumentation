/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReactorInstrumentationModule extends InstrumentationModule {

  public ReactorInstrumentationModule() {
    super("reactor", "reactor-3.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HooksInstrumentation());
  }
}
