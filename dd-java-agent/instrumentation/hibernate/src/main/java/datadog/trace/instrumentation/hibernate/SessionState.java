package datadog.trace.instrumentation.hibernate;

import io.opentracing.Scope;
import io.opentracing.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public Span sessionSpan;
  public Scope methodScope;
  public boolean hasChildSpan = true;
}
