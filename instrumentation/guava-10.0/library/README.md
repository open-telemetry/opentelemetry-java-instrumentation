# Library Instrumentation for Guava version 10.0 and higher

When a method annotated with `@WithSpan` returns a Guava `ListenableFuture`, the OpenTelemetry
Java agent ends the span when the future completes rather than when the method returns. For example,
this measures the full latency of a call made through a
[gRPC future stub](https://grpc.io/docs/languages/java/generated-code/#future-stub).

## Quickstart

Follow the [Java agent setup for `@WithSpan`](https://opentelemetry.io/docs/zero-code/java/agent/annotations/)
and annotate a method whose declared return type is `ListenableFuture` or a subtype:

```java
import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@WithSpan
public ListenableFuture<LookupResponse> lookup(LookupRequest request) {
  return futureStub.lookup(request);
}
```

The agent includes this support, so you don't need to add `opentelemetry-guava-10.0` to your
application. The standalone artifact supplies only the span-ending strategy. Adding it to an
application that uses the Spring Boot starter does not make `@WithSpan` wait for the future.
