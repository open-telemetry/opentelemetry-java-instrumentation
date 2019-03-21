package datadog.trace.instrumentation.hibernate.core.v4_0;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hibernate.HibernateDecorator.DECORATOR;
import static datadog.trace.instrumentation.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.hibernate.SessionMethodUtils;
import datadog.trace.instrumentation.hibernate.SessionState;
import io.opentracing.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", SessionState.class.getName());
    map.put("org.hibernate.Query", SessionState.class.getName());
    map.put("org.hibernate.Transaction", SessionState.class.getName());
    map.put("org.hibernate.Criteria", SessionState.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.SharedSessionContract")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("close")).and(takesArguments(0)), SessionCloseAdvice.class.getName());

    // Session synchronous methods we want to instrument.
    transformers.put(
        isMethod()
            .and(
                named("save")
                    .or(named("replicate"))
                    .or(named("saveOrUpdate"))
                    .or(named("update"))
                    .or(named("merge"))
                    .or(named("persist"))
                    .or(named("lock"))
                    .or(named("refresh"))
                    .or(named("insert"))
                    .or(named("delete"))
                    // Iterator methods.
                    .or(named("iterate"))
                    // Lazy-load methods.
                    .or(named("immediateLoad"))
                    .or(named("internalLoad"))),
        SessionMethodAdvice.class.getName());
    // Handle the non-generic 'get' separately.
    transformers.put(
        isMethod()
            .and(named("get"))
            .and(returns(named("java.lang.Object")))
            .and(takesArgument(0, named("java.lang.String"))),
        SessionMethodAdvice.class.getName());

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a ContextStore.
    transformers.put(
        isMethod()
            .and(named("beginTransaction").or(named("getTransaction")))
            .and(returns(named("org.hibernate.Transaction"))),
        GetTransactionAdvice.class.getName());

    transformers.put(
        isMethod().and(returns(safeHasSuperType(named("org.hibernate.Query")))),
        GetQueryAdvice.class.getName());

    transformers.put(
        isMethod().and(returns(safeHasSuperType(named("org.hibernate.Criteria")))),
        GetCriteriaAdvice.class.getName());

    return transformers;
  }

  public static class SessionCloseAdvice extends V4Advice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This final SharedSessionContract session,
        @Advice.Thrown final Throwable throwable) {

      final ContextStore<SharedSessionContract, SessionState> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      final SessionState state = contextStore.get(session);
      if (state == null || state.getSessionSpan() == null) {
        return;
      }
      if (state.getMethodScope() != null) {
        state.getMethodScope().close();
      }

      final Span span = state.getSessionSpan();
      DECORATOR.onError(span, throwable);
      DECORATOR.beforeFinish(span);
      span.finish();
    }
  }

  public static class SessionMethodAdvice extends V4Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startMethod(
        @Advice.This final SharedSessionContract session,
        @Advice.Origin("#m") final String name,
        @Advice.Argument(0) final Object entity) {

      final boolean startSpan = !SCOPE_ONLY_METHODS.contains(name);
      final ContextStore<SharedSessionContract, SessionState> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      return SessionMethodUtils.startScopeFrom(
          contextStore, session, "hibernate." + name, entity, startSpan);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.This final SharedSessionContract session,
        @Advice.Enter final SessionState sessionState,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final Object returned) {

      SessionMethodUtils.closeScope(sessionState, throwable, returned);
    }
  }

  public static class GetQueryAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This final SharedSessionContract session, @Advice.Return final Query query) {

      final ContextStore<SharedSessionContract, SessionState> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      final ContextStore<Query, SessionState> queryContextStore =
          InstrumentationContext.get(Query.class, SessionState.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  public static class GetTransactionAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This final SharedSessionContract session,
        @Advice.Return final Transaction transaction) {

      final ContextStore<SharedSessionContract, SessionState> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      final ContextStore<Transaction, SessionState> transactionContextStore =
          InstrumentationContext.get(Transaction.class, SessionState.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }

  public static class GetCriteriaAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This final SharedSessionContract session, @Advice.Return final Criteria criteria) {

      final ContextStore<SharedSessionContract, SessionState> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      final ContextStore<Criteria, SessionState> criteriaContextStore =
          InstrumentationContext.get(Criteria.class, SessionState.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, criteriaContextStore, criteria);
    }
  }
}
