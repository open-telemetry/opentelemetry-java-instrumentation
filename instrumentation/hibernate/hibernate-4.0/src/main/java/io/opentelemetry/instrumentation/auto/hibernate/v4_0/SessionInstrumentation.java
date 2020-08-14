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

package io.opentelemetry.instrumentation.auto.hibernate.v4_0;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.instrumentation.auto.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.instrumentation.auto.hibernate.SessionMethodUtils;
import io.opentelemetry.trace.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", Span.class.getName());
    map.put("org.hibernate.Query", Span.class.getName());
    map.put("org.hibernate.Transaction", Span.class.getName());
    map.put("org.hibernate.Criteria", Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.SharedSessionContract"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("close")).and(takesArguments(0)),
        SessionInstrumentation.class.getName() + "$SessionCloseAdvice");

    // Session synchronous methods we want to instrument.
    transformers.put(
        isMethod()
            .and(
                namedOneOf(
                    "save",
                    "replicate",
                    "saveOrUpdate",
                    "update",
                    "merge",
                    "persist",
                    "lock",
                    "refresh",
                    "insert",
                    "delete",
                    // Lazy-load methods.
                    "immediateLoad",
                    "internalLoad")),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");
    // Handle the non-generic 'get' separately.
    transformers.put(
        isMethod()
            .and(named("get"))
            .and(returns(named("java.lang.Object")))
            .and(takesArgument(0, named("java.lang.String"))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a ContextStore.
    transformers.put(
        isMethod()
            .and(namedOneOf("beginTransaction", "getTransaction"))
            .and(returns(named("org.hibernate.Transaction"))),
        SessionInstrumentation.class.getName() + "$GetTransactionAdvice");

    transformers.put(
        isMethod().and(returns(hasInterface(named("org.hibernate.Query")))),
        SessionInstrumentation.class.getName() + "$GetQueryAdvice");

    transformers.put(
        isMethod().and(returns(hasInterface(named("org.hibernate.Criteria")))),
        SessionInstrumentation.class.getName() + "$GetCriteriaAdvice");

    return transformers;
  }

  public static class SessionCloseAdvice extends V4Advice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This final SharedSessionContract session,
        @Advice.Thrown final Throwable throwable) {

      ContextStore<SharedSessionContract, Span> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      Span sessionSpan = contextStore.get(session);
      if (sessionSpan == null) {
        return;
      }

      DECORATE.onError(sessionSpan, throwable);
      DECORATE.beforeFinish(sessionSpan);
      sessionSpan.end();
    }
  }

  public static class SessionMethodAdvice extends V4Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope startMethod(
        @Advice.This final SharedSessionContract session,
        @Advice.Origin("#m") final String name,
        @Advice.Argument(0) final Object entity) {

      boolean startSpan = !SCOPE_ONLY_METHODS.contains(name);
      ContextStore<SharedSessionContract, Span> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      return SessionMethodUtils.startScopeFrom(
          contextStore, session, "Session." + name, entity, startSpan);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final Object returned,
        @Advice.Origin("#m") final String name) {

      SessionMethodUtils.closeScope(spanWithScope, throwable, "Session." + name, returned);
    }
  }

  public static class GetQueryAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This final SharedSessionContract session, @Advice.Return final Query query) {

      ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      ContextStore<Query, Span> queryContextStore =
          InstrumentationContext.get(Query.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  public static class GetTransactionAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This final SharedSessionContract session,
        @Advice.Return final Transaction transaction) {

      ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      ContextStore<Transaction, Span> transactionContextStore =
          InstrumentationContext.get(Transaction.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }

  public static class GetCriteriaAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This final SharedSessionContract session, @Advice.Return final Criteria criteria) {

      ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      ContextStore<Criteria, Span> criteriaContextStore =
          InstrumentationContext.get(Criteria.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, criteriaContextStore, criteria);
    }
  }
}
