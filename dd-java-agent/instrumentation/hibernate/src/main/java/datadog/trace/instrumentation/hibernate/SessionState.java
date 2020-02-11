package datadog.trace.instrumentation.hibernate;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public AgentSpan sessionSpan;
  public AgentScope methodScope;
  public boolean hasChildSpan = true;
}
