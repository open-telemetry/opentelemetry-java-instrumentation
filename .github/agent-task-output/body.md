Consolidates the Couchbase 2.6 network instrumentation into the Couchbase 2.0 Gradle project while keeping separate Muzzle checks and runtime test suites for pre-2.6 and 2.6+ clients.

Existing settings such as `otel.instrumentation.couchbase-2.6.enabled=false` retain their meaning. Network enrichment, instrumentation scope names, shared helper projects, and Couchbase 3.x instrumentation remain unchanged.

Part of #20189.
