Expand Couchbase 3.x javaagent test coverage across supported SDK versions.

- Verify async missing-document tracing for Couchbase 3.0.
- Verify async upsert operation, request encoding, and server dispatch spans for Couchbase 3.1 and 3.2.
- Assert operation spans are roots and SDK lifecycle spans are their children.

Fixes #20006
