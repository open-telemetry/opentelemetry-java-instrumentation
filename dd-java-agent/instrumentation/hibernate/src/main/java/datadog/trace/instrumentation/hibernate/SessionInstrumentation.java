package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hibernate.SessionMethodUtils.entityName;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
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
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Session;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.hibernate.query.spi.QueryImplementor;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends Instrumenter.Default {

  public SessionInstrumentation() {
    super("hibernate");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.Session", SessionState.class.getName());
    map.put("org.hibernate.query.Query", SessionState.class.getName());
    map.put("org.hibernate.Transaction", SessionState.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.hibernate.SessionMethodUtils",
      "datadog.trace.instrumentation.hibernate.SessionState",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named("org.hibernate.Session")
                    .or(named("org.hibernate.internal.AbstractSharedSessionContract"))));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("close"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Session"))))
            .and(takesArguments(0)),
        SessionCloseAdvice.class.getName());

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
                    .or(named("delete")))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Session")))),
        SessionMethodAdvice.class.getName());
    // Handle the generic and non-generic 'get' separately.
    transformers.put(
        isMethod()
            .and(named("get"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Session"))))
            .and(returns(named("java.lang.Object")))
            .and(takesArgument(0, named("java.lang.String"))),
        SessionMethodAdvice.class.getName());

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to
    // the returned object using a ContextStore.
    transformers.put(
        isMethod()
            .and(named("beginTransaction").or(named("getTransaction")))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.SharedSessionContract"))))
            .and(takesArguments(0))
            .and(returns(named("org.hibernate.Transaction"))),
        GetTransactionAdvice.class.getName());
    transformers.put(
        isMethod()
            .and(named("createQuery"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.SharedSessionContract")))),
        GetQueryAdvice.class.getName());

    return transformers;
  }

  public static class SessionCloseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This final Session session, @Advice.Thrown final Throwable throwable) {

      final ContextStore<Session, SessionState> contextStore =
          InstrumentationContext.get(Session.class, SessionState.class);
      final SessionState state = contextStore.get(session);
      if (state == null || state.getSessionSpan() == null) {
        return;
      }
      if (state.getMethodScope() != null) {
        System.err.println("THIS IS WRONG"); // TODO: proper warning/logging.
      }
      final Span span = state.getSessionSpan();

      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      span.finish();
    }
  }

  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startSave(
        @Advice.This final Session session,
        @Advice.Origin("#m") final String name,
        @Advice.Argument(0) final Object entity) {

      final ContextStore<Session, SessionState> contextStore =
          InstrumentationContext.get(Session.class, SessionState.class);
      return SessionMethodUtils.startScopeFrom(
          contextStore, session, "hibernate." + name, entityName(entity));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endSave(
        @Advice.This final Session session,
        @Advice.Enter final SessionState sessionState,
        @Advice.Thrown final Throwable throwable) {

      SessionMethodUtils.closeScope(sessionState, throwable);
    }
  }

  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This final SharedSessionContract session,
        @Advice.Return(readOnly = false) final QueryImplementor query) {

      if (!(query instanceof Query)) {
        return;
      }

      final ContextStore<Session, SessionState> sessionContextStore =
          InstrumentationContext.get(Session.class, SessionState.class);
      final ContextStore<Query, SessionState> queryContextStore =
          InstrumentationContext.get(Query.class, SessionState.class);

      SessionMethodUtils.attachSpanFromSession(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This final SharedSessionContract session,
        @Advice.Return(readOnly = false) final Transaction transaction) {

      final ContextStore<Session, SessionState> sessionContextStore =
          InstrumentationContext.get(Session.class, SessionState.class);
      final ContextStore<Transaction, SessionState> transactionContextStore =
          InstrumentationContext.get(Transaction.class, SessionState.class);

      SessionMethodUtils.attachSpanFromSession(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }
}
