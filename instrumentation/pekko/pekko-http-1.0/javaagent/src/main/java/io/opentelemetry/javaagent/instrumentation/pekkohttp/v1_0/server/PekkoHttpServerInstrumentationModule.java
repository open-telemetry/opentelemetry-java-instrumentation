/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class PekkoHttpServerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public PekkoHttpServerInstrumentationModule() {
    super("pekko-http", "pekko-http-1.0", "pekko-http-server");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // in GraphInterpreterInstrumentation we instrument a class that belongs to pekko-streams, make
    // sure this runs only when pekko-http is present to avoid muzzle failures
    return hasClassesNamed("org.apache.pekko.http.scaladsl.HttpExt");
  }

  @Override
  public String getModuleGroup() {
    return "pekko-server";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HttpExtServerInstrumentation(),
        new GraphInterpreterInstrumentation(),
        new PekkoHttpServerSourceInstrumentation());
  }
}
