/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v2_3;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Struts2InstrumentationModule extends InstrumentationModule {

  public Struts2InstrumentationModule() {
    super("struts", "struts-2.3");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActionInvocationInstrumentation());
  }
}
