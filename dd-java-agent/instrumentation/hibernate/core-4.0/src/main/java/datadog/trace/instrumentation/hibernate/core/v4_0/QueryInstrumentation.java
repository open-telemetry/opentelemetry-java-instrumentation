package datadog.trace.instrumentation.hibernate.core.v4_0;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hibernate.HibernateDecorator.DECORATOR;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.hibernate.SessionMethodUtils;
import datadog.trace.instrumentation.hibernate.SessionState;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Query;
import org.hibernate.SQLQuery;

@AutoService(Instrumenter.class)
public class QueryInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.hibernate.Query", SessionState.class.getName());
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.Query")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(
                named("list")
                    .or(named("executeUpdate"))
                    .or(named("uniqueResult"))
                    .or(named("scroll"))),
        QueryMethodAdvice.class.getName());
  }

  public static class QueryMethodAdvice extends V4Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startMethod(
        @Advice.This final Query query, @Advice.Origin("#m") final String name) {

      final ContextStore<Query, SessionState> contextStore =
          InstrumentationContext.get(Query.class, SessionState.class);

      // Note: We don't know what the entity is until the method is returning.
      final SessionState state =
          SessionMethodUtils.startScopeFrom(
              contextStore, query, "hibernate.query." + name, null, true);
      if (state != null) {
        DECORATOR.onStatement(state.getMethodScope().span(), query.getQueryString());
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.This final Query query,
        @Advice.Enter final SessionState state,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final Object returned) {

      Object entity = returned;
      if (returned == null || query instanceof SQLQuery) {
        // Not a method that returns results, or the query returns a table rather than an ORM
        // object.
        entity = query.getQueryString();
      }

      SessionMethodUtils.closeScope(state, throwable, entity);
    }
  }
}
