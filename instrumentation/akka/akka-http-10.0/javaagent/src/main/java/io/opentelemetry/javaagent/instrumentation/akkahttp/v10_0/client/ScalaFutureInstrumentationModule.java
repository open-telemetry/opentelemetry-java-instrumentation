/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ScalaFutureInstrumentationModule extends InstrumentationModule {
  public ScalaFutureInstrumentationModule() {
    super("akka-http-10.0", "akka-http-client", "akka-http", "akka");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ScalaPromiseInstrumentation());
  }
}
