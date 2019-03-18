package datadog.trace.instrumentation.hibernate.v3_5;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.hibernate.v4_0.SessionMethodUtils;
import datadog.trace.instrumentation.hibernate.v4_0.SessionState;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Transaction;

@AutoService(Instrumenter.class)
public class TransactionInstrumentation extends Instrumenter.Default {

  public TransactionInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.hibernate.Transaction", SessionState.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SessionMethodUtils",
      packageName + ".SessionState",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      "datadog.trace.agent.decorator.OrmClientDecorator",
      packageName + ".HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.Transaction")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("commit")).and(takesArguments(0)),
        TransactionCommitAdvice.class.getName());
  }

  public static class TransactionCommitAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startCommit(@Advice.This final Transaction transaction) {

      final ContextStore<Transaction, SessionState> contextStore =
          InstrumentationContext.get(Transaction.class, SessionState.class);

      return SessionMethodUtils.startScopeFrom(
          contextStore, transaction, "hibernate.transaction.commit", null, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endCommit(
        @Advice.This final Transaction transaction,
        @Advice.Enter final SessionState state,
        @Advice.Thrown final Throwable throwable) {

      SessionMethodUtils.closeScope(state, throwable, null);
    }
  }
}
