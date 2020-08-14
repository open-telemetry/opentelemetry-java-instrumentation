/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.hibernate.v3_3;

import static io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator.TRACER;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
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
    stores.put("org.hibernate.Session", Span.class.getName());
    stores.put("org.hibernate.StatelessSession", Span.class.getName());
    stores.put("org.hibernate.SharedSessionContract", Span.class.getName());
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
    public static void openSession(@Advice.Return final Object session) {

      Span span = TRACER.spanBuilder("Session").startSpan();
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, session);

      if (session instanceof Session) {
        ContextStore<Session, Span> contextStore =
            InstrumentationContext.get(Session.class, Span.class);
        contextStore.putIfAbsent((Session) session, span);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Span> contextStore =
            InstrumentationContext.get(StatelessSession.class, Span.class);
        contextStore.putIfAbsent((StatelessSession) session, span);
      }
    }
  }
}
