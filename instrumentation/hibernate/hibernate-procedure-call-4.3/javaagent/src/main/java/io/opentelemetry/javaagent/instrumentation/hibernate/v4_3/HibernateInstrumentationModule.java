/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class HibernateInstrumentationModule extends InstrumentationModule {
  public HibernateInstrumentationModule() {
    super("hibernate-procedure-call", "hibernate-procedure-call-4.3", "hibernate");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.hibernate.procedure.ProcedureCall");
  }

  @Override
  public boolean isIndyModule() {
    // uses SessionInfo class from hibernate common which is now in separate class loader for all
    // instrumentations
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ProcedureCallInstrumentation(), new SessionInstrumentation());
  }
}
