/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AkkaHttpClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AkkaHttpClientInstrumentationModule() {
    super("akka-http", "akka-http-10.0", "akka-http-client");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("akka.http.scaladsl.HttpExt");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpExtClientInstrumentation(), new PoolMasterActorInstrumentation());
  }
}
