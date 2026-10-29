# [General] Context in client and producer hooks and callbacks

This guidance applies to library and javaagent instrumentation, whether or not it uses the
`Instrumenter` API.

## Choose context by the code being invoked

Classify each hook or callback by the work it invokes, not by request versus response or before
versus after timing. Application code that contributes to request or body generation, consumes a
response, or handles completion or failure runs with the caller `Context` captured before the
outgoing operation. This includes application-defined hooks invoked before sending, whether they
run inline or asynchronously. Preserve an application’s explicit context choices.

Use the outgoing operation’s `Context` for library-internal execution that performs or continues
the instrumented operation, and where required for internal instrumentation or duplicate-span
suppression. Do not make every request hook inherit the outgoing span merely because it runs
before sending. Application callback work is typically a sibling of the outgoing span, but
CLIENT and PRODUCER spans need not be leaves: justified internal spans may be children.

Capture and restore the full caller `Context`, including when it has no span or carries custom
values. Restore the callback thread’s previous context after invoking application code. A
`Scope` is thread-bound and must be closed on the thread where it was opened; it is distinct from
the asynchronous operation span’s lifetime. When propagating remotely, inject the context
containing the outgoing span even when application hooks or callbacks run with the caller
context.

Independently instrumented outgoing work started by application callback code must use that
callback’s intended context. Do not accidentally suppress it or parent it to the enclosing
outgoing span by leaving the operation context current.

## Separate span lifetime from context scope

Ideally, the outgoing span starts after application request preparation and ends before application
response handling, so its duration excludes that application work. Temporal overlap is acceptable
when the client does not expose suitable instrumentation hooks. Whether or not their execution
overlaps the span, application hooks and callbacks run under the captured caller `Context`.

For asynchronous operations, close the initiating thread’s scope when the initiating method
returns, and end the operation span once at its actual terminal completion. Do not end the span
just because request/body generation or a nonterminal response callback runs. A terminal
completion callback may coincide with that boundary; its context scope remains the captured
caller context regardless of span-ending order.

## Test the context boundaries

Cover application request and body generation, response consumption (including deferred demand),
and completion and failure callbacks. Exercise inline and asynchronous execution. Verify behavior
with no caller span, with custom caller `Context` values, and restoration of an unrelated context
on the callback thread. Check that outgoing propagation includes the outgoing span, and that
independently instrumented outgoing work from callbacks is neither suppressed nor parented to the
enclosing operation. Allow justified internal spans and duplicate-span suppression; do not assume
the CLIENT or PRODUCER span is always a leaf.
