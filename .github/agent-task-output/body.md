Captures gRPC server spans for calls to unregistered services when stable RPC semantic conventions are enabled. These spans use `_OTHER` for `rpc.method` and record the requested method in `rpc.method_original`.

Library users can enable this behavior by configuring the server builder:

```java
GrpcTelemetry.create(openTelemetry).configureServerBuilder(serverBuilder);
```

`createServerInterceptor()` remains available, but it cannot see calls to unregistered services.
