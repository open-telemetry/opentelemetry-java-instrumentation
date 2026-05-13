/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.mojarra.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MojarraInstrumentationModule extends InstrumentationModule {
  public MojarraInstrumentationModule() {
    super("jsf-mojarra", "jsf-mojarra-3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.0 (renamed from javax.faces)
    return hasClassesNamed("jakarta.faces.context.FacesContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ActionListenerImplInstrumentation(), new RestoreViewPhaseInstrumentation());
  }
}
