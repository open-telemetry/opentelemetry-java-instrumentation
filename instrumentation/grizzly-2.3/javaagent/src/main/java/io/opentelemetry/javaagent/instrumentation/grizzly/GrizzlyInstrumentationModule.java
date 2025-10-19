/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GrizzlyInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public GrizzlyInstrumentationModule() {
    super("grizzly", "grizzly-2.3");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in 2.3
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

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
