package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import lombok.NonNull;

public class SessionState {
  @NonNull public final Span sessionSpan;
  public SpanScopePair methodScope;
  private boolean endSpan;
  public boolean hasChildSpan = true;

  public SessionState(@NonNull final Span sessionSpan) {
    this.sessionSpan = sessionSpan;
  }

  public @NonNull Span getSessionSpan() {
    return sessionSpan;
  }

  public SpanScopePair getMethodScope() {
    return methodScope;
  }

  public void setMethodScope(final SpanScopePair methodScope, final boolean endSpan) {
    this.methodScope = methodScope;
    this.endSpan = endSpan;
  }

  public void setHasChildSpan(final boolean hasChildSpan) {
    this.hasChildSpan = hasChildSpan;
  }

  public void endScope() {
    if (endSpan) {
      methodScope.getSpan().end();
    }
    methodScope.getScope().close();
  }
}
