package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

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
import org.hibernate.Criteria;

@AutoService(Instrumenter.class)
public class CriteriaInstrumentation extends Instrumenter.Default {

  public CriteriaInstrumentation() {
    super("hibernate");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.Criteria", SessionState.class.getName());
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
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.Criteria")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("list").or(named("uniqueResult")).or(named("scroll"))),
        CriteriaMethodAdvice.class.getName());

    return transformers;
  }

  public static class CriteriaMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startMethod(
        @Advice.This final Criteria criteria, @Advice.Origin("#m") final String name) {

      final ContextStore<Criteria, SessionState> contextStore =
          InstrumentationContext.get(Criteria.class, SessionState.class);

      return SessionMethodUtils.startScopeFrom(
          contextStore, criteria, "hibernate.criteria." + name);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.This final Criteria criteria,
        @Advice.Enter final SessionState state,
        @Advice.Thrown final Throwable throwable) {

      SessionMethodUtils.closeScope(state, throwable);
    }
  }
}
