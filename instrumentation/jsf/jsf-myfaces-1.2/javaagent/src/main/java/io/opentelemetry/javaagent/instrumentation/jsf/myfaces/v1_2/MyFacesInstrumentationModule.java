/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.myfaces.v1_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MyFacesInstrumentationModule extends InstrumentationModule {
  public MyFacesInstrumentationModule() {
    super("jsf-myfaces", "jsf-myfaces-1.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 3.0 (renamed to jakarta.faces)
    return hasClassesNamed("javax.faces.context.FacesContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ActionListenerImplInstrumentation(), new RestoreViewExecutorInstrumentation());
  }
}
