/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cfml;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CfmlInstrumentationModule extends InstrumentationModule {
  public CfmlInstrumentationModule() {
    super("cfml");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CfmlUDFMethodInstrumentation());
  }
}
