/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;

import io.opentelemetry.javaagent.tooling.Instrumenter;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.SharedSessionContract;

public abstract class AbstractHibernateInstrumentation extends Instrumenter.Default {

  public AbstractHibernateInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.hibernate.Session");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils",
      "io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator",
      packageName + ".AbstractHibernateInstrumentation$V4Advice",
    };
  }

  public abstract static class V4Advice {

    /**
     * Some cases of instrumentation will match more broadly than others, so this unused method
     * allows all instrumentation to uniformly match versions of Hibernate starting at 4.0.
     */
    public static void muzzleCheck(SharedSessionContract contract) {
      contract.createCriteria("");
    }
  }
}
