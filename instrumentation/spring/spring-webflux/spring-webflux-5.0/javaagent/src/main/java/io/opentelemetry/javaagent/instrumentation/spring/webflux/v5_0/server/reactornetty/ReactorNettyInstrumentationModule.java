/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.reactornetty;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReactorNettyInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ReactorNettyInstrumentationModule() {
    super("spring-webflux", "spring-webflux-5.0", "reactor-netty", "reactor-netty-server");
  }

  @Override
  public String getModuleGroup() {
    // relies on netty
    return "netty";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new HttpTrafficHandlerInstrumentation(), new ContextHandlerInstrumentation());
  }
}
