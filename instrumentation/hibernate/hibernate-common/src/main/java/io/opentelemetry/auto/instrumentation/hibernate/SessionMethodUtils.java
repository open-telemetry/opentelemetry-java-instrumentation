/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.hibernate;

import static io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator.TRACER;

import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SessionMethodUtils {

  public static final Set<String> SCOPE_ONLY_METHODS =
      new HashSet<>(Arrays.asList("immediateLoad", "internalLoad"));

  // Starts a scope as a child from a Span, where the Span is attached to the given spanKey using
  // the given contextStore.
  public static <TARGET, ENTITY> SpanWithScope startScopeFrom(
      final ContextStore<TARGET, Span> contextStore,
      final TARGET spanKey,
      final String operationName,
      final ENTITY entity,
      final boolean createSpan) {

    final Span sessionSpan = contextStore.get(spanKey);
    if (sessionSpan == null) {
      return null; // No state found. We aren't in a Session.
    }

    final int depth = CallDepthThreadLocalMap.incrementCallDepth(SessionMethodUtils.class);
    if (depth > 0) {
      return null; // This method call is being traced already.
    }

    if (createSpan) {
      final Span span =
          TRACER
              .spanBuilder(DECORATE.spanNameForOperation(operationName, entity))
              .setParent(sessionSpan)
              .startSpan();
      DECORATE.afterStart(span);
      return new SpanWithScope(span, TRACER.withSpan(span));
    } else {
      return new SpanWithScope(null, TRACER.withSpan(sessionSpan));
    }
  }

  // Closes a Scope/Span, adding an error tag if the given Throwable is not null.
  public static void closeScope(
      final SpanWithScope spanWithScope,
      final Throwable throwable,
      final String operationName,
      final Object entity) {

    if (spanWithScope == null) {
      // This method call was re-entrant. Do nothing, since it is being traced by the parent/first
      // call.
      return;
    }
    CallDepthThreadLocalMap.reset(SessionMethodUtils.class);

    final Span span = spanWithScope.getSpan();
    if (span != null) {
      DECORATE.onError(span, throwable);
      if (operationName != null && entity != null) {
        final String entityName = DECORATE.entityName(entity);
        if (entityName != null) {
          span.updateName(operationName + " " + entityName);
        }
      }
      DECORATE.beforeFinish(span);
      span.end();
    }
    spanWithScope.closeScope();
  }

  // Copies a span from the given Session ContextStore into the targetContextStore. Used to
  // propagate a Span from a Session to transient Session objects such as Transaction and Query.
  public static <S, T> void attachSpanFromStore(
      final ContextStore<S, Span> sourceContextStore,
      final S source,
      final ContextStore<T, Span> targetContextStore,
      final T target) {

    final Span sessionSpan = sourceContextStore.get(source);
    if (sessionSpan == null) {
      return;
    }

    targetContextStore.putIfAbsent(target, sessionSpan);
  }
}
