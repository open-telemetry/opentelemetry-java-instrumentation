package datadog.trace.instrumentation.hibernate;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class SessionState {
  @NonNull public AgentSpan sessionSpan;
  public AgentScope methodScope;
  public boolean hasChildSpan = true;
}
