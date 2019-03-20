package datadog.trace.instrumentation.hibernate;

import static datadog.trace.instrumentation.hibernate.HibernateDecorator.DECORATOR;

import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.ContextStore;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
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

    final Scope scope;
    if (createSpan) {
      scope =
          GlobalTracer.get()
              .buildSpan(operationName)
              .asChildOf(sessionState.getSessionSpan())
              .startActive(true);
      DECORATOR.afterStart(scope.span());
      DECORATOR.onOperation(scope.span(), entity);
    } else {
      scope = GlobalTracer.get().scopeManager().activate(sessionState.getSessionSpan(), false);
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
    final Scope scope = sessionState.getMethodScope();
    final Span span = scope.span();
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
