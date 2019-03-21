package datadog.trace.instrumentation.hibernate.core.v3_5;

import datadog.trace.agent.tooling.Instrumenter;
import org.hibernate.EntityNameResolver;
import org.hibernate.classic.Validatable;

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
      packageName + ".AbstractHibernateInstrumentation$V3Advice",
    };
  }

  public abstract static class V3Advice {

    /**
     * Some cases of instrumentation will match more broadly than others, so this unused method
     * allows all instrumentation to uniformly match versions of Hibernate between 3.5 and 4.
     */
    public static void muzzleCheck(
        // Not in 4.0
        final Validatable validatable,
        // Not before 3.2.0.GA (Can't find anything not before 3.5)
        final EntityNameResolver resolver) {
      validatable.validate();
      resolver.resolveEntityName(null);
    }
  }
}
