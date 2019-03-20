package datadog.trace.instrumentation.hibernate.core.v4_0;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;

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
    return classLoaderHasClasses("org.hibernate.SharedSessionContract");
  }
}
