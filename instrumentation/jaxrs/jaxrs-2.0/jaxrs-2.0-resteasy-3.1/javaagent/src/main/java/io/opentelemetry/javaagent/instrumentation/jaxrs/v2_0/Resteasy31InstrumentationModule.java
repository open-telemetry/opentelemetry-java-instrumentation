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
public class Resteasy31InstrumentationModule extends InstrumentationModule {
  public Resteasy31InstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "resteasy", "resteasy-3.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "javax.ws.rs.Path",
        "org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new Resteasy31RequestContextInstrumentation(),
        new ResteasyServletContainerDispatcherInstrumentation(),
        new ResteasyRootNodeTypeInstrumentation(),
        new ResteasyResourceMethodInvokerInstrumentation(),
        new ResteasyResourceLocatorInvokerInstrumentation());
  }
}
