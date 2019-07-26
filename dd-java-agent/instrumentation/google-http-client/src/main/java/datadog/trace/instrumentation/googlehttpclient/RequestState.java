package datadog.trace.instrumentation.googlehttpclient;

import io.opentracing.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class RequestState {
  @NonNull public Span span;
}
