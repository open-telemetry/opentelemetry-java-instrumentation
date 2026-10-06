/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.v2_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GrizzlyInstrumentationModule extends InstrumentationModule {
  public GrizzlyInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "grizzly-2.3" : "grizzly",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"grizzly"}
            : new String[] {"grizzly-2.3"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.3
    return hasClassesNamed("org.glassfish.grizzly.InputSource");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultFilterChainInstrumentation(),
        new FilterInstrumentation(),
        new HttpCodecFilterInstrumentation(),
        new HttpServerFilterInstrumentation(),
        new HttpHandlerInstrumentation(),
        new FilterChainContextInstrumentation());
  }
}
