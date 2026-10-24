# [General] Context in client and producer hooks and callbacks

This guidance applies to library and javaagent instrumentation, whether or not it uses the
`Instrumenter` API.

For outgoing client and producer operations, distinguish code that performs the operation from
application code that handles its result.

Make the operation's context current while executing the instrumented library operation,
including request-preparation interceptors and before-send hooks within that execution. This
lets those hooks access the operation's span and lets nested instrumentation apply its
suppression rules.

Invoke application response handlers and completion callbacks with the caller's context
captured before starting the operation, rather than the client or producer context. Spans
created by this application code should be siblings of the outgoing span, not its children.
This also applies when the library invokes a callback synchronously before the initiating
method returns.

For example, for a simple request with an active caller span, the expected relationships are:

```text
caller
|-- outgoing client operation
|   `-- remote server operation
`-- application callback work
```

Without an active caller span, application callback work should not inherit the outgoing
span. Restore the captured context even when it contains no span, rather than leaving the
completion thread's context active.

## Separate span lifetime from context scope

For asynchronous operations, close the initiating thread's scope when the initiating method
returns, and end the span when the operation finishes. Any scope opened on another thread must
be closed on that same thread.

For a terminal completion callback, end the operation before invoking application code under
the captured caller context. Ending a span does not remove it from the current context.
Application response callbacks that run before completion also use the caller's context, but
do not end the operation prematurely.

Classify hooks and callbacks by what they do. An internal callback may still perform part of
the instrumented operation, while a response handler invoked inside a synchronous client
method may already be application continuation code.

## Test the context boundaries

Check callback-created spans with and without a caller span, on success and failure. Cover
inline completion where the library supports it, as well as asynchronous completion. Verify
that the callback's scope restores the previous context on exit.

Request-preparation and before-send hooks should observe the outgoing span when they execute
within the instrumented operation.
