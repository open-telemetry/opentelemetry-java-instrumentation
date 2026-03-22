/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PekkoHttpServerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public PekkoHttpServerInstrumentationModule() {
    super("pekko-http", "pekko-http-1.0", "pekko-http-server");
  }

  @Override
  public String getModuleGroup() {
    return "pekko-server";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HttpExtServerInstrumentation(),
        new HttpServerBluePrintInstrumentation(),
        new GraphInterpreterInstrumentation(),
        new PekkoHttpServerSourceInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
