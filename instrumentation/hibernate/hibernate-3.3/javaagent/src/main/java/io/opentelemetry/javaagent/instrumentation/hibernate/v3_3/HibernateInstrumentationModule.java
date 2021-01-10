/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.Criteria", Context.class.getName());
    map.put("org.hibernate.Query", Context.class.getName());
    map.put("org.hibernate.Session", Context.class.getName());
    map.put("org.hibernate.StatelessSession", Context.class.getName());
    map.put("org.hibernate.Transaction", Context.class.getName());
    return Collections.unmodifiableMap(map);
  }
}
