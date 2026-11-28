/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RestletInstrumentationModule extends InstrumentationModule {

  public RestletInstrumentationModule() {
    super("restlet-2.5", "restlet-2.0", "restlet");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.restlet.Request")
        .and(not(hasClassesNamed("org.restlet.engine.component.ComponentXmlParser")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ServerInstrumentation(), new RouteInstrumentation());
  }
}
