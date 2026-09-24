Cloud Native Gateway requests made through `couchbase2://` now report the configured endpoint in `server.address`. Port 18098 is treated as the scheme default, so it is omitted from `server.port`; an explicitly configured non-default port is still reported.

Protostellar-specific instrumentation uses a separate Muzzle-scoped module, preserving the existing module's Couchbase 3.2 minimum.
