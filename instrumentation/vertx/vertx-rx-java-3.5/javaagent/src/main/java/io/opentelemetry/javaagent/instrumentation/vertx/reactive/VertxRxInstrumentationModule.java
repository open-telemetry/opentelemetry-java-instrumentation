/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxRxInstrumentationModule extends InstrumentationModule {

  public VertxRxInstrumentationModule() {
    super("vertx-rx-java", "vertx-rx-java-3.5", "vertx");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AsyncResultSingleInstrumentation());
  }
}
