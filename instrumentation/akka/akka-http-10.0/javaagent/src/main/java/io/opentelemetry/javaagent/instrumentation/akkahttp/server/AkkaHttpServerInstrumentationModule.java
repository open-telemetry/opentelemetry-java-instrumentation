/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaHttpServerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AkkaHttpServerInstrumentationModule() {
    super("akka-http", "akka-http-10.0", "akka-http-server");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public String getModuleGroup() {
    return "akka-http";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HttpExtServerInstrumentation(),
        new GraphInterpreterInstrumentation(),
        new AkkaHttpServerSourceInstrumentation());
  }
}
