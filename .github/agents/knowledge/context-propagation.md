# [General] Context propagation and parent selection

Use `Context.current()`, or the context captured for the operation, as the base for incoming request
spans. Extract propagation against that context so a remote parent can replace the ambient span
without discarding unrelated context values. Preserve normal instrumenter suppression. This applies
to library and javaagent instrumentation.

Extract against `Context.root()` only when the extracted context must represent what the carrier
contains, independently of the receiving thread's context.

For example, extracting a message's producer context for span links should identify only what the
message carries. An ambient consumer span is not evidence of a propagated producer context. Using
ambient context as the extraction base can invent a producer link when propagation is absent.
The processing span can still use the current or captured operation context as its parent.
