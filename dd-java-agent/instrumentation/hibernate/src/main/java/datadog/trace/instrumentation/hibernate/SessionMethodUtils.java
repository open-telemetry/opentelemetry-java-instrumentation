package datadog.trace.instrumentation.hibernate;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.ContextStore;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import javax.persistence.Entity;
import org.hibernate.SharedSessionContract;

public class SessionMethodUtils {

  public static String entityName(final Object entityOrName) {
    String name = "unknown object";
    if (entityOrName == null) {
      return name; // Hibernate can internally call save(null, Entity).
    }
    if (entityOrName instanceof String) {
      name = (String) entityOrName;
    } else if (entityOrName.getClass().isAnnotationPresent(Entity.class)) {
      // This is the object being replicated.
      name = entityOrName.getClass().getName();
    }
    return name;
  }

  // Starts a scope as a child from a Span, where the Span is attached to the given spanKey using
  // the given contextStore.
  public static <T> SessionState startScopeFrom(
      final ContextStore<T, SessionState> contextStore,
      final T spanKey,
      final String operationName,
      final String resourceName) {

    final SessionState sessionState = contextStore.get(spanKey);

    if (sessionState == null) {
      // No state found. Maybe the instrumentation isn't working correctly.
      return null;
    }

    if (sessionState.getMethodScope() != null) {
      // This method call was re-entrant. Do nothing, since it is being traced by the parent/first
      // call.
      return sessionState;
    }

    final Scope scope =
        GlobalTracer.get()
            .buildSpan(operationName)
            .asChildOf(sessionState.getSessionSpan())
            .withTag(DDTags.SERVICE_NAME, "hibernate")
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HIBERNATE)
            .withTag(DDTags.RESOURCE_NAME, resourceName)
            .withTag(Tags.COMPONENT.getKey(), "hibernate-java")
            .startActive(true);
    sessionState.setMethodScope(scope);

    return sessionState;
  }

  public static <T> SessionState startScopeFrom(
      final ContextStore<T, SessionState> contextStore,
      final T spanKey,
      final String operationName) {
    return startScopeFrom(contextStore, spanKey, operationName, operationName);
  }

  // Closes a Scope/Span, adding an error tag if the given Throwable is not null.
  public static void closeScope(final SessionState sessionState, final Throwable throwable) {

    if (sessionState == null || sessionState.getMethodScope() == null) {
      // This method call was re-entrant. Do nothing, since it is being traced by the parent/first
      // call.
      return;
    }

    final Scope scope = sessionState.getMethodScope();
    final Span span = scope.span();
    if (throwable != null) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
    }

    span.finish();
    scope.close();
    sessionState.setMethodScope(null);
  }

  // Copies a span from the given Session ContextStore into the targetContextStore. Used to
  // propagate a Span from a Session to transient Session objects such as Transaction and Query.
  public static <T> void attachSpanFromSession(
      final ContextStore<SharedSessionContract, SessionState> sessionContextStore,
      final SharedSessionContract session,
      final ContextStore<T, SessionState> targetContextStore,
      final T target) {

    if (!(session instanceof SharedSessionContract)
        || sessionContextStore == null
        || targetContextStore == null) {
      return;
    }

    final SessionState state = sessionContextStore.get((SharedSessionContract) session);
    if (state == null) {
      return;
    }

    targetContextStore.putIfAbsent(target, state);
  }
}
