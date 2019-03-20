package datadog.trace.instrumentation.hibernate.core.v3_5;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractHibernateInstrumentation extends Instrumenter.Default {

  public AbstractHibernateInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.hibernate.SessionMethodUtils",
      "datadog.trace.instrumentation.hibernate.SessionState",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      "datadog.trace.agent.decorator.OrmClientDecorator",
      "datadog.trace.instrumentation.hibernate.HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("org.hibernate.SharedSessionContract"))
        .and(classLoaderHasClasses("org.hibernate.EntityNameResolver"));
  }
}
