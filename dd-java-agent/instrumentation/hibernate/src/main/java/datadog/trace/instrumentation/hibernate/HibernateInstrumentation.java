package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
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
import org.hibernate.internal.AbstractSharedSessionContract;
import org.hibernate.query.Query;
import org.hibernate.query.spi.QueryImplementor;

@AutoService(Instrumenter.class)
public class HibernateInstrumentation extends Instrumenter.Default {

  public HibernateInstrumentation() {
    super("hibernate");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.Session", Span.class.getName());
    map.put("org.hibernate.query.Query", Span.class.getName());
    map.put("org.hibernate.Transaction", Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.hibernate.SessionMethodUtils",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named("org.hibernate.SessionFactory")
                    .or(named("org.hibernate.Session"))
                    .or(named("org.hibernate.internal.AbstractSharedSessionContract"))));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("openSession"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.SessionFactory"))))
            .and(takesArguments(0))
            .and(returns(named("org.hibernate.Session"))),
        SessionFactoryAdvice.class.getName());
    transformers.put(
        isMethod()
            .and(named("close"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Session"))))
            .and(takesArguments(0)),
        SessionCloseAdvice.class.getName());
    transformers.put(
        isMethod()
            .and(named("save"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Session"))))
            .and(takesArguments(1)),
        SessionSaveAdvice.class.getName());
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
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.SharedSessionContract"))))
            .and(takesArguments(1)),
        GetQueryAdvice.class.getName());

    return transformers;
  }

  public static class SessionFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return(readOnly = false) final Session session) {

      final Span span =
          GlobalTracer.get()
              .buildSpan("hibernate.session")
              .withTag(DDTags.SERVICE_NAME, "hibernate")
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HIBERNATE)
              .withTag(Tags.COMPONENT.getKey(), "hibernate-java")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .start();

      final ContextStore<Session, Span> contextStore =
          InstrumentationContext.get(Session.class, Span.class);
      contextStore.putIfAbsent(session, span);
    }
  }

  public static class SessionCloseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This final Session session, @Advice.Thrown final Throwable throwable) {

      final ContextStore<Session, Span> contextStore =
          InstrumentationContext.get(Session.class, Span.class);
      final Span span = contextStore.get(session);

      if (span == null) {
        return;
      }
      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      span.finish();
    }
  }

  public static class SessionSaveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSave(
        @Advice.This final Session session, @Advice.Argument(0) final Object entity) {

      final ContextStore<Session, Span> contextStore =
          InstrumentationContext.get(Session.class, Span.class);
      return SessionMethodUtils.startScopeFrom(contextStore, session, "hibernate.save");
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endSave(
        @Advice.This final Session session,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      SessionMethodUtils.closeScope(scope, throwable);
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

      final ContextStore<Session, Span> sessionContextStore =
          InstrumentationContext.get(Session.class, Span.class);
      final ContextStore<Query, Span> queryContextStore =
          InstrumentationContext.get(Query.class, Span.class);

      SessionMethodUtils.attachSpanFromSession(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This final AbstractSharedSessionContract session,
        @Advice.Return(readOnly = false) final Transaction transaction) {

      final ContextStore<Session, Span> sessionContextStore =
          InstrumentationContext.get(Session.class, Span.class);
      final ContextStore<Transaction, Span> transactionContextStore =
          InstrumentationContext.get(Transaction.class, Span.class);

      SessionMethodUtils.attachSpanFromSession(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }
}
