/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class HibernateInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public HibernateInstrumentationModule() {
    super("hibernate", "hibernate-6.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // not present before 6.0
        "org.hibernate.query.spi.SqmQuery");
  }

  @Override
  public String getModuleGroup() {
    return "hibernate";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new QueryInstrumentation(),
        new SessionFactoryInstrumentation(),
        new SessionInstrumentation(),
        new TransactionInstrumentation());
  }
}
