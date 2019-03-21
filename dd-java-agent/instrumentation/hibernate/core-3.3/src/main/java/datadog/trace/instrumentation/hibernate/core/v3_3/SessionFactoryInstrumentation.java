package datadog.trace.instrumentation.hibernate.core.v3_3;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hibernate.HibernateDecorator.DECORATOR;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.hibernate.SessionState;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

@AutoService(Instrumenter.class)
public class SessionFactoryInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> stores = new HashMap<>();
    stores.put("org.hibernate.Session", SessionState.class.getName());
    stores.put("org.hibernate.StatelessSession", SessionState.class.getName());
    stores.put("org.hibernate.SharedSessionContract", SessionState.class.getName());
    return stores;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.SessionFactory")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("openSession").or(named("openStatelessSession")))
            .and(takesArguments(0))
            .and(
                returns(
                    named("org.hibernate.Session")
                        .or(named("org.hibernate.StatelessSession"))
                        .or(safeHasSuperType(named("org.hibernate.Session"))))),
        SessionFactoryAdvice.class.getName());
  }

  public static class SessionFactoryAdvice extends V3Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return final Object session) {

      final Span span = GlobalTracer.get().buildSpan("hibernate.session").start();
      DECORATOR.afterStart(span);
      DECORATOR.onConnection(span, session);

      if (session instanceof Session) {
        final ContextStore<Session, SessionState> contextStore =
            InstrumentationContext.get(Session.class, SessionState.class);
        contextStore.putIfAbsent((Session) session, new SessionState(span));
      } else if (session instanceof StatelessSession) {
        final ContextStore<StatelessSession, SessionState> contextStore =
            InstrumentationContext.get(StatelessSession.class, SessionState.class);
        contextStore.putIfAbsent((StatelessSession) session, new SessionState(span));
      }
    }
  }
}
