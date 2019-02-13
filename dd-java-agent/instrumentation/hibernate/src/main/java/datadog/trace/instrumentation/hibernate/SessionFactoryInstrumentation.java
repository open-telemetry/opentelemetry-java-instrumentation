package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
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
import org.hibernate.SharedSessionContract;

@AutoService(Instrumenter.class)
public class SessionFactoryInstrumentation extends Instrumenter.Default {

  public SessionFactoryInstrumentation() {
    super("hibernate");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", SessionState.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.hibernate.SessionState",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.SessionFactory")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // Session lifecycle. A span will cover openSession->Session.close, but no scope will be
    // generated.
    transformers.put(
        isMethod()
            .and(named("openSession").or(named("openStatelessSession")))
            .and(takesArguments(0))
            .and(
                returns(
                    named("org.hibernate.Session").or(named("org.hibernate.StatelessSession")))),
        SessionFactoryAdvice.class.getName());

    return transformers;
  }

  public static class SessionFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return final SharedSessionContract session) {

      final Span span =
          GlobalTracer.get()
              .buildSpan("hibernate.session")
              .withTag(DDTags.SERVICE_NAME, "hibernate")
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HIBERNATE)
              .withTag(Tags.COMPONENT.getKey(), "hibernate-java")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .start();

      final ContextStore<SharedSessionContract, SessionState> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      contextStore.putIfAbsent(session, new SessionState(span));
    }
  }
}
