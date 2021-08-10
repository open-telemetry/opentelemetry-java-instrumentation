/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxrsInstrumentationModule extends InstrumentationModule {
  public JaxrsInstrumentationModule() {
    super("jaxrs", "jaxrs-1.0");
  }

  // this is required to make sure instrumentation won't apply to jax-rs 2
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("javax.ws.rs.container.AsyncResponse"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JaxrsAnnotationsInstrumentation());
  }
}
