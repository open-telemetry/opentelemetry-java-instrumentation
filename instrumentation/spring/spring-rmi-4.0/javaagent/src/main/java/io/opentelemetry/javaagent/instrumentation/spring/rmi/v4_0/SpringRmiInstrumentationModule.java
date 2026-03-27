/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.client.ClientInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server.ServerInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringRmiInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringRmiInstrumentationModule() {
    super("spring-rmi", "spring-rmi-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.springframework.context.annotation.Condition");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientInstrumentation(), new ServerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
