package datadog.trace.instrumentation.googlehttpclient;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class RequestState {
  @NonNull public AgentSpan span;
}
