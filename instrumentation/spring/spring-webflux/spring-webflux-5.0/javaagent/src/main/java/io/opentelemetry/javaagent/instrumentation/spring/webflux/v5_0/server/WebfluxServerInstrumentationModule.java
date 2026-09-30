/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class WebfluxServerInstrumentationModule extends InstrumentationModule {

  public WebfluxServerInstrumentationModule() {
    super(
        "spring-webflux",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-webflux-5.0", "spring-webflux-5.0-server"}
            : new String[] {"spring-webflux-5.0", "spring-webflux-server"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DispatcherHandlerInstrumentation(),
        new HandlerAdapterInstrumentation(),
        new RouterFunctionInstrumentation());
  }
}
