Adds `network.peer.address` and `network.peer.port` to stable Lettuce 5.x database spans. Retries and cluster redirects report the final socket peer while configured `server.*` attributes still identify the original target.

Batch spans report a peer only when every command uses the same resolved address. Commands that end before a peer is known omit these attributes. Legacy semantic convention telemetry stays unchanged.

Reactive commands keep separate span state for each subscription, including resubscriptions, native tracing, and cancellation.

Stacked on #20077. Part of #20010 and #19899.
