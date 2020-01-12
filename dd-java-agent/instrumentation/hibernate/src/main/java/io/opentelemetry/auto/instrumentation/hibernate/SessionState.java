package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public AgentSpan sessionSpan;
  public AgentScope methodScope;
  public boolean hasChildSpan = true;
}
