package datadog.trace.instrumentation.couchbase.client;

import io.opentracing.Span;
import lombok.Data;
import lombok.NonNull;

@Data
public class CouchbaseRequestState {
  @NonNull public Span span;
}
