/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry.v5_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class TapestryInstrumentationModule extends InstrumentationModule {

  public TapestryInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "tapestry-5.4" : "tapestry",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"tapestry"}
            : new String[] {"tapestry-5.4"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.4.0
    return hasClassesNamed("org.apache.tapestry5.Binding2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new InitializeActivePageNameInstrumentation(),
        new ComponentPageElementImplInstrumentation());
  }
}
