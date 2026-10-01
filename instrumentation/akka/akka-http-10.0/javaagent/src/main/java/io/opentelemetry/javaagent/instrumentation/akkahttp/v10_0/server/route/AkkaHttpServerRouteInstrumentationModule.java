/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server.route;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * This instrumentation applies to classes in akka-http.jar while
 * AkkaHttpServerInstrumentationModule applies to classes in akka-http-core.jar
 */
@AutoService(InstrumentationModule.class)
public class AkkaHttpServerRouteInstrumentationModule extends InstrumentationModule {
  public AkkaHttpServerRouteInstrumentationModule() {
    super(
        "akka-http",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"akka-http-10.0", "akka-http-server"}
            : new String[] {"akka-http-10.0", "akka-http-server", "akka-http-server-route"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PathMatcherInstrumentation(),
        new PathMatcherStaticInstrumentation(),
        new RouteConcatenationInstrumentation());
  }
}
