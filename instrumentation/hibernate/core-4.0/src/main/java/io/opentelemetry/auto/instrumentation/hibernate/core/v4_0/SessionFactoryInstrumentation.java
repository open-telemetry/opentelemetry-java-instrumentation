package io.opentelemetry.auto.instrumentation.hibernate.core.v4_0;

import static io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator.DECORATOR;
import static io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.SharedSessionContract;

@AutoService(Instrumenter.class)
public class SessionFactoryInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.hibernate.SharedSessionContract", Span.class.getName());
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
                    named("org.hibernate.Session").or(named("org.hibernate.StatelessSession")))),
        SessionFactoryInstrumentation.class.getName() + "$SessionFactoryAdvice");
  }

  public static class SessionFactoryAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return final SharedSessionContract session) {

      final Span span = TRACER.spanBuilder("hibernate.session").setSpanKind(CLIENT).startSpan();
      DECORATOR.afterStart(span);
      DECORATOR.onConnection(span, session);

      final ContextStore<SharedSessionContract, Span> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      contextStore.putIfAbsent(session, span);
    }
  }
}
