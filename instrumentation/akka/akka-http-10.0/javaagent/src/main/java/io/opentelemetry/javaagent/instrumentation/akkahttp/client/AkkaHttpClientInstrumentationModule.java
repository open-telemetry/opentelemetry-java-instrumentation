/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AkkaHttpClientInstrumentationModule extends InstrumentationModule {
  public AkkaHttpClientInstrumentationModule() {
    super("akka-http", "akka-http-client");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.opentelemetry.javaagent.instrumentation.akkahttp")
        || className.startsWith("org.json");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpExtClientInstrumentation(), new PoolMasterActorInstrumentation());
  }
}
