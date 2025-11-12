/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JFinalInstrumentationModule extends InstrumentationModule {
  public JFinalInstrumentationModule() {
    super("jfinal", "jfinal-3.6");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.jfinal.core.ActionMapping");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ActionMappingInstrumentation(), new ActionHandlerInstrumentation());
  }
}
