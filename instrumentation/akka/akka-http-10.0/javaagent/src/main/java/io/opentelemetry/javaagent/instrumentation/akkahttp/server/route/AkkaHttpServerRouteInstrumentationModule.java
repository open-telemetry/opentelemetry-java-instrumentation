/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

/**
 * This instrumentation applies to classes in akka-http.jar while
 * AkkaHttpServerInstrumentationModule applies to classes in akka-http-core.jar
 */
@AutoService(InstrumentationModule.class)
public class AkkaHttpServerRouteInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AkkaHttpServerRouteInstrumentationModule() {
    super("akka-http", "akka-http-10.0", "akka-http-server", "akka-http-server-route");
  }

  @Override
  public String getModuleGroup() {
    return "akka-http";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PathMatcherInstrumentation(),
        new PathMatcherStaticInstrumentation(),
        new RouteConcatenationInstrumentation(),
        new PathConcatenationInstrumentation());
  }
}
