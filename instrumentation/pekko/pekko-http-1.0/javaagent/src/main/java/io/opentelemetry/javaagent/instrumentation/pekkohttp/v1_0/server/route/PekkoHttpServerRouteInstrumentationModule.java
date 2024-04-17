/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * This instrumentation applies to classes in pekko-http.jar while
 * PekkoHttpServerInstrumentationModule applies to classes in pekko-http-core.jar
 */
@AutoService(InstrumentationModule.class)
public class PekkoHttpServerRouteInstrumentationModule extends InstrumentationModule {
  public PekkoHttpServerRouteInstrumentationModule() {
    super("pekko-http", "pekko-http-1.0", "pekko-http-server", "pekko-http-server-route");
  }

  @Override
  public boolean isIndyModule() {
    // PekkoHttpServerInstrumentationModule and PekkoHttpServerRouteInstrumentationModule share
    // PekkoRouteHolder class
    return false;
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
