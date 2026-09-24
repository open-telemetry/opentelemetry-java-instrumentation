An AWS SDK v2 SQS `receiveMessage` call returns a list, not an application processing callback. Code that walks that list has no other observable Process boundary; a Spring Cloud AWS listener may later observe the same message and compete with raw SDK processing.

This applies the processing-ownership guidance in #20188 to SDK v2 SQS traversal and Spring Cloud AWS listeners.

Add best-effort processing to supported iterators, list iterators, sublists, `forEach`, and spliterators, including split callbacks. A supported Spring Cloud AWS single-message listener takes ownership instead, ending its operation on synchronous or asynchronous callback completion; unsupported batch listeners keep the SDK fallback. An abandoned iterator has no reliable completion event.

Process metrics remain separate from enclosing operations. This does not solve cross-layer `messaging.client.consumed.messages` deduplication, tracked in #20214.
