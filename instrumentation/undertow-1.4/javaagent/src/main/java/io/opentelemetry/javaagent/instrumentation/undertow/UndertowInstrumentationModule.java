/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class UndertowInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public UndertowInstrumentationModule() {
    super("undertow", "undertow-1.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in 1.4.0
    return hasClassesNamed("io.undertow.Undertow$ListenerInfo");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HandlerInstrumentation(),
        new HttpServerExchangeInstrumentation(),
        new HttpServerConnectionInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
