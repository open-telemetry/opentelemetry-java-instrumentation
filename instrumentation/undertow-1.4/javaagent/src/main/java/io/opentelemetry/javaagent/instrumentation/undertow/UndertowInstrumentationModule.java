/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class UndertowInstrumentationModule extends InstrumentationModule {

  public UndertowInstrumentationModule() {
    super("undertow", "undertow-1.4");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HandlerInstrumentation(), new HttpServerExchangeInstrumentation());
  }
}
