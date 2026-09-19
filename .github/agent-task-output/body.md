Updates stable Jedis 3.x telemetry to report the Redis target configured by the application instead of the endpoint selected for each command. This applies to direct, pooled, sharded, cluster, and Sentinel clients, including span names and database client duration metrics.

Legacy telemetry continues to report the selected endpoint. Multi-endpoint targets keep shard order, sort cluster and Sentinel endpoints, and include at most five endpoints.
