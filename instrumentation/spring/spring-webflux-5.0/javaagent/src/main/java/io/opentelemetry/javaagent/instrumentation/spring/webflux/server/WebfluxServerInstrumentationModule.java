/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WebfluxServerInstrumentationModule extends InstrumentationModule {

  public WebfluxServerInstrumentationModule() {
    super("spring-webflux", "spring-webflux-5.0", "spring-webflux-server");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DispatcherHandlerInstrumentation(),
        new HandlerAdapterInstrumentation(),
        new RouterFunctionInstrumentation());
  }
}
