/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator.TRACER;
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
    return singletonMap("org.hibernate.SharedSessionContract", Context.class.getName());
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
            .and(returns(namedOneOf("org.hibernate.Session", "org.hibernate.StatelessSession"))),
        SessionFactoryInstrumentation.class.getName() + "$SessionFactoryAdvice");
  }

  public static class SessionFactoryAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return SharedSessionContract session) {

      Context context = Context.current();
      Span span = TRACER.spanBuilder("Session").setParent(context).startSpan();
      DECORATE.afterStart(span);

      ContextStore<SharedSessionContract, Context> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      contextStore.putIfAbsent(session, withSpan(span, context));
    }
  }
}
