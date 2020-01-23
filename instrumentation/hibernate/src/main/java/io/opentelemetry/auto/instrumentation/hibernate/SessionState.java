package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.trace.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public final Span sessionSpan;
}
