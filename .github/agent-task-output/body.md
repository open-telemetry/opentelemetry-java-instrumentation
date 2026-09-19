Cloud Native Gateway requests made through `couchbase2://` now report the configured endpoint in `server.address`. Port 18098 is treated as the scheme default, so it is omitted from `server.port`; an explicitly configured non-default port is still reported.

`CoreProtostellar` and `ProtostellarBaseRequest` are absent from the module's minimum Couchbase 3.2 dependency, so their configured-target associations use dynamically registered virtual fields without raising the supported version floor.
