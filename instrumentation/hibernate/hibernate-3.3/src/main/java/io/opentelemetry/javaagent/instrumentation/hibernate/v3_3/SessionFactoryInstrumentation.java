/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.hibernate.v3_3;

import static io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator.TRACER;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
    Map<String, String> stores = new HashMap<>();
    stores.put("org.hibernate.Session", Context.class.getName());
    stores.put("org.hibernate.StatelessSession", Context.class.getName());
    stores.put("org.hibernate.SharedSessionContract", Context.class.getName());
    return stores;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.SessionFactory"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(namedOneOf("openSession", "openStatelessSession"))
            .and(takesArguments(0))
            .and(
                returns(
                    namedOneOf("org.hibernate.Session", "org.hibernate.StatelessSession")
                        .or(hasInterface(named("org.hibernate.Session"))))),
        SessionFactoryInstrumentation.class.getName() + "$SessionFactoryAdvice");
  }

  public static class SessionFactoryAdvice extends V3Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return Object session) {

      Context context = Context.current();
      Span span = TRACER.spanBuilder("Session").setParent(context).startSpan();
      DECORATE.afterStart(span);

      if (session instanceof Session) {
        ContextStore<Session, Context> contextStore =
            InstrumentationContext.get(Session.class, Context.class);
        contextStore.putIfAbsent((Session) session, withSpan(span, context));
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> contextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        contextStore.putIfAbsent((StatelessSession) session, withSpan(span, context));
      }
    }
  }
}
