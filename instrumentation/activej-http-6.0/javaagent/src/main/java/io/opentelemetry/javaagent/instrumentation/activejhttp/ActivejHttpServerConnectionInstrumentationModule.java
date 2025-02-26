/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ActivejHttpServerConnectionInstrumentationModule extends InstrumentationModule {

  public ActivejHttpServerConnectionInstrumentationModule() {
    super("activej-http", "activej-http-6.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActivejHttpServerConnectionInstrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class which was added in 6.0, the minimum version we support.
    return hasClassesNamed("io.activej.http.HttpServer");
  }
}
