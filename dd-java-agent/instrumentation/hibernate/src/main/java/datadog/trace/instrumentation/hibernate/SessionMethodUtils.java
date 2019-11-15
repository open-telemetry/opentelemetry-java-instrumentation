package datadog.trace.instrumentation.hibernate;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.hibernate.HibernateDecorator.DECORATOR;

import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SessionMethodUtils {

  public static final Set<String> SCOPE_ONLY_METHODS =
      new HashSet<>(Arrays.asList("immediateLoad", "internalLoad"));

  // Starts a scope as a child from a Span, where the Span is attached to the given spanKey using
  // the given contextStore.
  public static <TARGET, ENTITY> SessionState startScopeFrom(
      final ContextStore<TARGET, SessionState> contextStore,
      final TARGET spanKey,
      final String operationName,
      final ENTITY entity,
      final boolean createSpan) {

    final SessionState sessionState = contextStore.get(spanKey);

    if (sessionState == null) {
      return null; // No state found. We aren't in a Session.
    }

    final int depth = CallDepthThreadLocalMap.incrementCallDepth(SessionMethodUtils.class);
    if (depth > 0) {
      return null; // This method call is being traced already.
    }

    final AgentScope scope;
    if (createSpan) {
      final AgentSpan span = startSpan(operationName, sessionState.getSessionSpan().context());
      DECORATOR.afterStart(span);
      DECORATOR.onOperation(span, entity);
      scope = activateSpan(span, true);
    } else {
      scope = activateSpan(sessionState.getSessionSpan(), false);
      sessionState.setHasChildSpan(false);
    }

    sessionState.setMethodScope(scope);
    return sessionState;
  }

  // Closes a Scope/Span, adding an error tag if the given Throwable is not null.
  public static void closeScope(
      final SessionState sessionState, final Throwable throwable, final Object entity) {

    if (sessionState == null || sessionState.getMethodScope() == null) {
      // This method call was re-entrant. Do nothing, since it is being traced by the parent/first
      // call.
      return;
    }

    CallDepthThreadLocalMap.reset(SessionMethodUtils.class);
    final AgentScope scope = sessionState.getMethodScope();
    final AgentSpan span = scope.span();
    if (span != null && sessionState.hasChildSpan) {
      DECORATOR.onError(span, throwable);
      if (entity != null) {
        DECORATOR.onOperation(span, entity);
      }
      DECORATOR.beforeFinish(span);
      span.finish();
    }

    scope.close();
    sessionState.setMethodScope(null);
  }

  // Copies a span from the given Session ContextStore into the targetContextStore. Used to
  // propagate a Span from a Session to transient Session objects such as Transaction and Query.
  public static <S, T> void attachSpanFromStore(
      final ContextStore<S, SessionState> sourceContextStore,
      final S source,
      final ContextStore<T, SessionState> targetContextStore,
      final T target) {

    final SessionState state = sourceContextStore.get(source);
    if (state == null) {
      return;
    }

    targetContextStore.putIfAbsent(target, state);
  }
}
