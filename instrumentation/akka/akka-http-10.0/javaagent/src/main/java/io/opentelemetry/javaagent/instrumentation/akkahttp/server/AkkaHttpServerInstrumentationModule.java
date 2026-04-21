/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AkkaHttpServerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AkkaHttpServerInstrumentationModule() {
    super("akka-http", "akka-http-10.0", "akka-http-server");
  }

  @Override
  public String getModuleGroup() {
    return "akka-http";
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // in GraphInterpreterInstrumentation we instrument a class that belongs to akka-streams, make
    // sure this runs only when akka-http is present to avoid muzzle failures
    return hasClassesNamed("akka.http.scaladsl.HttpExt");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HttpExtServerInstrumentation(),
        new GraphInterpreterInstrumentation(),
        new AkkaHttpServerSourceInstrumentation());
  }
}
