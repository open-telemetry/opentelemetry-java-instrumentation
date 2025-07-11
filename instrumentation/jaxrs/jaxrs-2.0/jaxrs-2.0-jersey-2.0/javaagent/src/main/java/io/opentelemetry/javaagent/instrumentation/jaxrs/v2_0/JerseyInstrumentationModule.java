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
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JerseyInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public JerseyInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "jersey", "jersey-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.ws.rs.Path", "org.glassfish.jersey.server.ContainerRequest");
  }

  @Override
  public String getModuleGroup() {
    // depends on Servlet3SnippetInjectingResponseWrapper
    return "servlet";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JerseyRequestContextInstrumentation(),
        new JerseyServletContainerInstrumentation(),
        new JerseyResourceMethodDispatcherInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
