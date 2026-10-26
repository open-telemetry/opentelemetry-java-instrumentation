# [General] Context propagation and parent selection

This guidance applies to library and javaagent instrumentation.

## Start with the operation's context

Use `Context.current()` at the incoming request boundary, or the context captured for the operation,
as the base for request-span creation. A `Context` contains more than its current span. Baggage,
application values, and instrumentation suppression state can belong to an outer layer.

Extract incoming propagation against that base context. A valid propagated remote parent can replace
the ambient span without discarding unrelated context values. Without a propagated parent, the
ambient parent remains. With neither an ambient span nor a propagated parent, the span naturally
has no parent.

Preserve ordinary suppression through the normal `Instrumenter.shouldStart()` and `start()` path.
A propagating instrumenter extracts in `start()`, so pass the base context to both methods. When
extracting explicitly before using a non-propagating instrumenter, pass the extracted context to
both methods.

For asynchronous work, capture the context when the operation starts or work is handed off. Retain
it for the matching callback and completion. The completion thread's current context is not a
substitute for that captured parent.

## Reconstruct transported context independently

Use `Context.root()` when the extraction result must describe only the context carried with a message,
independently of the receiving thread. This is different from choosing the base context for a request
or processing span.

Trace how consumers use the extraction result before changing its base. Extracting a producer context
against ambient consumer context can invent a producer link when the message carries none. Choosing
the processing span's parent is a separate decision and can still preserve its ambient parent.
