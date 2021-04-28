/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class HibernateInstrumentationModule extends InstrumentationModule {

  public HibernateInstrumentationModule() {
    super("hibernate", "hibernate-3.3");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // Not in 4.0
        "org.hibernate.classic.Validatable",
        // Not before 3.3.0.GA
        "org.hibernate.transaction.JBossTransactionManagerLookup");
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
}
