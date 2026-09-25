Expand Couchbase 3.x javaagent test coverage across supported SDK versions.

- Verify async missing-document tracing for Couchbase 3.0.
- Verify async upsert operation, request encoding, and server dispatch spans for Couchbase 3.1 and 3.2.
- Assert operation spans are roots and SDK lifecycle spans are their children.
- Clarify that the Couchbase SDK owns lifecycle hooks while the agent owns the tracing bridge and adapters.

Fixes #20006
