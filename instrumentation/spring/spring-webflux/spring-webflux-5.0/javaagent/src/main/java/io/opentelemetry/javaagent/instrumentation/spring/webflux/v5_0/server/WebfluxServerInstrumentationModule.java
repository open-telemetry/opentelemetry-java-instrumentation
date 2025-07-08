/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WebfluxServerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

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

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
