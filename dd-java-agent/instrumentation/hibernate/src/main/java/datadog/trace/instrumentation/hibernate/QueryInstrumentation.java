package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.query.Query;

@AutoService(Instrumenter.class)
public class QueryInstrumentation extends Instrumenter.Default {

  public QueryInstrumentation() {
    super("hibernate");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.query.Query", SessionState.class.getName());
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
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.query.Query")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("list"))
            .and(isDeclaredBy(safeHasSuperType(named("org.hibernate.Query"))))
            .and(takesArguments(0)),
        QueryListAdvice.class.getName());

    return transformers;
  }

  public static class QueryListAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startList(@Advice.This final Query query) {

      final ContextStore<Query, SessionState> contextStore =
          InstrumentationContext.get(Query.class, SessionState.class);

      return SessionMethodUtils.startScopeFrom(contextStore, query, "hibernate.query.list");
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endList(
        @Advice.This final Query query,
        @Advice.Enter final SessionState state,
        @Advice.Thrown final Throwable throwable) {

      SessionMethodUtils.closeScope(state, throwable);
    }
  }
}
