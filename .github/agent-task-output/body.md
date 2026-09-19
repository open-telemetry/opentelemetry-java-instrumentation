Reports the Redis socket used by each Jedis 2.x operation as `network.peer.address` and `network.peer.port` in stable database semantic convention mode, while preserving the configured `server.*` target from #20073.

Handles reconnects, pipeline and transaction batches, cluster retries and redirections, `runWithAnyNode` health checks, and sharded fan-out. Ambiguous, closed, and unresolved sockets remain unset.

Stacked on #20073. Part of #20010 and #19899.
