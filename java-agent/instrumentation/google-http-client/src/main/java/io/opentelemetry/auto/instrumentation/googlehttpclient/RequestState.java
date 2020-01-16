package io.opentelemetry.auto.instrumentation.googlehttpclient;

import io.opentelemetry.trace.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class RequestState {
  @NonNull public Span span;
}
