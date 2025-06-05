/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxClientInstrumentationModule extends InstrumentationModule {

  public VertxClientInstrumentationModule() {
    super("vertx-http-client", "vertx-http-client-4.0", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class removed in 4.0
    return not(hasClassesNamed("io.vertx.core.Starter"))
        // class added in 5.0
        .and(not(hasClassesNamed("io.vertx.core.http.impl.HttpClientConnectionInternal")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ConnectionManagerInstrumentation(),
        new HttpClientConnectionInstrumentation(),
        new HttpRequestInstrumentation());
  }
}
