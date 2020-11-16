/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator.DECORATE;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SessionMethodUtils {

  public static final Set<String> SCOPE_ONLY_METHODS =
      new HashSet<>(Arrays.asList("immediateLoad", "internalLoad"));

  // Starts a scope as a child from a Span, where the Span is attached to the given spanKey using
  // the given contextStore.
  public static <TargetT, EntityT> SpanWithScope startScopeFrom(
      ContextStore<TargetT, Context> contextStore,
      TargetT spanKey,
      String operationName,
      EntityT entity,
      boolean createSpan) {

    Context sessionContext = contextStore.get(spanKey);
    if (sessionContext == null) {
      return null; // No state found. We aren't in a Session.
    }

    int depth = CallDepthThreadLocalMap.incrementCallDepth(SessionMethodUtils.class);
    if (depth > 0) {
      return null; // This method call is being traced already.
    }

    if (createSpan) {
      Span span =
          tracer()
              .spanBuilder(DECORATE.spanNameForOperation(operationName, entity))
              .setParent(sessionContext)
              .startSpan();
      DECORATE.afterStart(span);
      return new SpanWithScope(span, sessionContext.with(span).makeCurrent());
    } else {
      return new SpanWithScope(null, sessionContext.makeCurrent());
    }
  }

  // Closes a Scope/Span, adding an error tag if the given Throwable is not null.
  public static void closeScope(
      SpanWithScope spanWithScope, Throwable throwable, String operationName, Object entity) {

    if (spanWithScope == null) {
      // This method call was re-entrant. Do nothing, since it is being traced by the parent/first
      // call.
      return;
    }
    CallDepthThreadLocalMap.reset(SessionMethodUtils.class);

    Span span = spanWithScope.getSpan();
    if (span != null) {
      DECORATE.onError(span, throwable);
      if (operationName != null && entity != null) {
        String entityName = DECORATE.entityName(entity);
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
      ContextStore<S, Context> sourceContextStore,
      S source,
      ContextStore<T, Context> targetContextStore,
      T target) {

    Context sessionContext = sourceContextStore.get(source);
    if (sessionContext == null) {
      return;
    }

    targetContextStore.putIfAbsent(target, sessionContext);
  }
}
