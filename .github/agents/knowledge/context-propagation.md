# [General] Context propagation and parent selection

Use this article when choosing the base context for incoming request spans, propagation extraction,
or context reconstructed from message metadata. It applies to library and javaagent instrumentation.

## Start with the operation's context

Use `Context.current()` at the incoming request boundary, or the context captured for that operation,
as the base for request-span creation. A `Context` contains more than its current span: baggage,
application values, and instrumentation suppression state can belong to an outer layer.

Extract incoming propagation against that base context. A valid propagated remote parent can replace
the ambient span; without a propagated parent, the ambient span remains available. Values not replaced
by the propagator remain in the context.

Pass the selected parent through the normal `Instrumenter.shouldStart()` and `start()` path, preserving
ordinary span suppression. A propagating instrumenter performs extraction in `start()`, so pass the
base context to both methods. When an integration extracts explicitly, use the operation's context as
the extraction base and pass the extracted context to both methods.

Lambda invocation handlers and Dubbo rejection handlers follow this rule. A rejection that cannot
read propagation headers or attachments can still use the operation's context. With neither an
ambient span nor a propagated parent, the resulting span naturally has no parent.

For asynchronous work, capture the context when the operation starts or work is handed off. Retain it
for the matching callback and completion; the completion thread's current context is not a substitute
for that captured parent.

Scope acquisition and closure have a separate lifecycle. See
[scope ownership](javaagent-advice-patterns.md#make-scope-ownership-visible) and
[messaging processing ownership](messaging-telemetry-ownership.md#finish-the-work-you-started).

## Reconstruct transported context independently

Use `Context.root()` when the extraction result must describe only the context carried with a message,
independently of the receiving thread. This is different from choosing the base context for a request
or processing span.

- Pekko remoting reconstructs sender context on a transport thread, stores it with the received
  message, and makes it current during actor delivery. Inheriting the transport thread's context
  would attach receiver-local state to the message.
- SQS creation-context extraction identifies the producer context used for span links and fallback
  parenting. An ambient consumer span is not evidence that the message carried a producer context.
  Processing separately honors its supplied parent and can use the creation context when that parent
  has no valid span.

Trace the extraction result's consumers before changing its base. Replacing root in a creation-context
extractor can invent a producer link when propagation is absent, even if a processing span should
preserve its ambient parent.
