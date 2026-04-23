/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.axis2.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Axis2InstrumentationModule extends InstrumentationModule {
  public Axis2InstrumentationModule() {
    super("axis2", "axis2-1.6", "jaxws");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.6.0
    return hasClassesNamed("org.apache.axis2.jaxws.api.MessageAccessor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new InvocationListenerRegistryTypeInstrumentation());
  }
}
