Reports configured Redis deployments in stable `server.address` and `server.port` for Rediscala direct clients, pools, replicated clients, clusters, Sentinel deployments, and transactions. These attributes describe the configured deployment instead of the network peer selected for a request. Legacy database semantic convention output does not change.

This is the Rediscala slice of #20010 and follows #19899.

Stacked on #20070, which introduces the shared `RedisServerTarget.ofEndpointAndUnorderedEndpoints` helper this PR reuses for master-and-replicas targets.
