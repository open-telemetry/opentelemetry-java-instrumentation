/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class HibernateInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public HibernateInstrumentationModule() {
    super("hibernate", "hibernate-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 4.0.0.Final
        "org.hibernate.internal.SessionFactoryImpl",
        // removed in 6.0.0.Final
        "org.hibernate.Criteria");
  }

  @Override
  public String getModuleGroup() {
    return "hibernate";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CriteriaInstrumentation(),
        new QueryInstrumentation(),
        new SessionFactoryInstrumentation(),
        new SessionInstrumentation(),
        new TransactionInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
