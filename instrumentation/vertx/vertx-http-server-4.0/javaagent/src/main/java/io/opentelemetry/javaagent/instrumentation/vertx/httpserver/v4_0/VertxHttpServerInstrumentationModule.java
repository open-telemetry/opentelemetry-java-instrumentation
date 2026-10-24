/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxHttpServerInstrumentationModule extends InstrumentationModule {

  public VertxHttpServerInstrumentationModule() {
    super("vertx-http-server-4.0", "vertx-http-server", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 4.0
    return not(hasClassesNamed("io.vertx.core.Starter"))
        // added in 5.0
        .and(not(hasClassesNamed("io.vertx.core.http.HttpClientConnection")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new Http2ServerRequestInstrumentation(),
        new Http2ServerResponseInstrumentation(),
        new Http2ServerStreamInstrumentation());
  }
}
