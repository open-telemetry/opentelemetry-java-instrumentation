---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Context propagation and scope ownership

Apply these rules to library and javaagent instrumentation, whether or not it uses `Instrumenter`.

## Incoming requests and carrier-only extraction

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

## Application hooks and callbacks

Classify a hook by the code it invokes, not by whether it runs before sending or after receiving.
Application code that prepares requests or bodies, consumes responses, or handles completion or
failure runs with the caller `Context` captured before the outgoing operation. This includes
application-defined hooks invoked inline before sending. Preserve the application's explicit
context choices.

Library-internal execution that performs or continues the instrumented operation uses the outgoing
operation's context, including where internal instrumentation or duplicate-span suppression needs
it. Application callback work is typically a sibling of the outgoing span, but justified internal
spans may be children; CLIENT and PRODUCER spans need not be leaves.

Capture the full caller context even when it contains no span or carries custom values. Restore
the callback thread's previous context after application code runs. Independently instrumented
outgoing work started by a callback must use the callback's intended context, without accidental
suppression or parenting to the enclosing outgoing span.

Remote propagation injects the context containing the outgoing span even when application hooks
run with the caller context.

## Span lifetime and thread scope

A `Scope` is thread-bound and closes on the thread where it opened. Its lifetime is distinct from
the asynchronous operation span's lifetime.

Ideally, an outgoing span starts after application request preparation and ends before application
response handling. Temporal overlap is acceptable when the client exposes no suitable hooks, but
application hooks still run under the captured caller context.

For asynchronous operations, close the initiating thread's scope when the initiating method
returns. End the span once at terminal completion, not at a request/body-generation hook or
nonterminal response callback. A terminal callback may coincide with completion; its application
context remains the captured caller context regardless of span-ending order.

## Coverage

When these boundaries change, cover the relevant application request/body generation, response
consumption including deferred demand, and completion/failure callbacks. Exercise supported inline
and asynchronous execution, no caller span, custom context values, and restoration of an unrelated
callback-thread context. Verify outgoing propagation and independent outgoing work from callbacks
without assuming the CLIENT or PRODUCER span is always a leaf.
