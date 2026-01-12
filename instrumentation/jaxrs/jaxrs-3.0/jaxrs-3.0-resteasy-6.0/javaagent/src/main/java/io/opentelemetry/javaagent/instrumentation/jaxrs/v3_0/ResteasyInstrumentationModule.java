/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ResteasyInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ResteasyInstrumentationModule() {
    super("jaxrs", "jaxrs-3.0", "resteasy", "resteasy-6.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "jakarta.ws.rs.Path",
        "org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ResteasyRequestContextInstrumentation(),
        new ResteasyServletContainerDispatcherInstrumentation(),
        new ResteasyRootNodeTypeInstrumentation(),
        new ResteasyResourceMethodInvokerInstrumentation(),
        new ResteasyResourceLocatorInvokerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
