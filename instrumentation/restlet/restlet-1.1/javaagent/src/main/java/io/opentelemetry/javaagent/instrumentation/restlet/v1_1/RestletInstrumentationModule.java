/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RestletInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public RestletInstrumentationModule() {
    super("restlet", "restlet-1.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 2.0
    return hasClassesNamed("org.restlet.data.Request");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ServerInstrumentation(), new RouteInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
