/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxrsInstrumentationModule extends InstrumentationModule {
  public JaxrsInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0");
  }

  // require jax-rs 2
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.ws.rs.container.AsyncResponse");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContainerRequestFilterInstrumentation(),
        new DefaultRequestContextInstrumentation(),
        new JaxrsAnnotationsInstrumentation(),
        new JaxrsAsyncResponseInstrumentation());
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }
}
