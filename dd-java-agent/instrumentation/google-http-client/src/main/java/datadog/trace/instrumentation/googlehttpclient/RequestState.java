package datadog.trace.instrumentation.googlehttpclient;

import datadog.trace.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class RequestState {
  @NonNull public AgentSpan span;
}
