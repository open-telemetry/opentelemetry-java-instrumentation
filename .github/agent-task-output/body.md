A Pulsar delivery can pass through the client receive hook and a Spring Pulsar listener before reaching application code. Both layers can observe processing, while nested listeners or multi-topic consumers can disturb a thread-wide receive-suppression flag and lose the message's parent context.

This applies the processing-ownership guidance in #20188 to Pulsar receives and Spring Pulsar listeners.

Carry receive context with the delivered message and let the Spring listener own its Process span only when it handles that delivery. Otherwise Pulsar remains the processing fallback. Restore suppression through nested callbacks and failed receives so another delivery retains its own parent, duration, and completion.

Cross-layer `messaging.client.consumed.messages` deduplication remains in #20214.
