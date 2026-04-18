/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_2;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JFinalInstrumentationModule extends InstrumentationModule {

  public JFinalInstrumentationModule() {
    super("jfinal", "jfinal-3.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ActionMappingInstrumentation(), new InvocationInstrumentation());
  }
}
