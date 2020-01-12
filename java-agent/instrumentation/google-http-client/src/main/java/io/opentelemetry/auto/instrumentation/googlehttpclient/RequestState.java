package io.opentelemetry.auto.instrumentation.googlehttpclient;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import lombok.Data;
import lombok.NonNull;

@Data
public class RequestState {
  @NonNull public AgentSpan span;
}
