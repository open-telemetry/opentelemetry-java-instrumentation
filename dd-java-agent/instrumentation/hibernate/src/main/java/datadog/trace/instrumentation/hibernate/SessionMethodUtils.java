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
import org.hibernate.Session;
import org.hibernate.SharedSessionContract;

public class SessionMethodUtils {

  // Starts a scope as a child from a Span, where the Span is attached to the given spanKey using
  // the given contextStore.
  public static <T> Scope startScopeFrom(
      final ContextStore<T, Span> contextStore, final T spanKey, final String operationName) {

    final Span span = contextStore.get(spanKey);

    final Scope scope =
        GlobalTracer.get()
            .buildSpan(operationName)
            .asChildOf(span) // Can be null.
            .withTag(DDTags.SERVICE_NAME, "hibernate")
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HIBERNATE)
            .withTag(Tags.COMPONENT.getKey(), "hibernate-java")
            .startActive(true);

    return scope;
  }

  // Closes a Scope/Span, adding an error tag if the given Throwable is not null.
  public static void closeScope(final Scope scope, final Throwable throwable) {

    final Span span = scope.span();
    if (throwable != null) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
    }

    span.finish();
    scope.close();
  }

  // Copies a span from the given Session ContextStore into the targetContextStore. Used to
  // propagate a Span from a Session to transient Session objects such as Transaction and Query.
  public static <T> void attachSpanFromSession(
      final ContextStore<Session, Span> sessionContextStore,
      final SharedSessionContract session,
      final ContextStore<T, Span> targetContextStore,
      final T target) {

    if (!(session instanceof Session)
        || sessionContextStore == null
        || targetContextStore == null) {
      return;
    }

    final Span span = sessionContextStore.get((Session) session);
    if (span == null) {
      return;
    }

    targetContextStore.putIfAbsent(target, span);
  }
}
