# [General] Context propagation and parent selection

Use `Context.current()`, or the context captured for the operation, as the base for incoming request
spans. Extract propagation against that context so a remote parent can replace the ambient span
without discarding unrelated context values. Preserve normal instrumenter suppression. This applies
to library and javaagent instrumentation.

Extract against `Context.root()` when reconstructing transported context independently of the
receiving thread, rather than choosing a request span's parent.
