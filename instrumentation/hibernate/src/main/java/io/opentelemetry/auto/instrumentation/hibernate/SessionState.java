package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.trace.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public Span sessionSpan;
  public CloseableSpanScopePair methodScope;
  public boolean hasChildSpan = true;
}
