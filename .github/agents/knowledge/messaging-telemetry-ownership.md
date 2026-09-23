# [Messaging] Processing ownership

Use when a client and a framework can observe the same messaging processing work.

Choose one owner for each concrete callback or observable traversal, not one owner for the message
across its entire lifetime. A retry, a second handler, or a nested delivery can be separate work.
The selected layer owns that work's process span, parent context, error, duration when recorded,
and completion. Receive, Send, and Create spans and context propagation have different boundaries.
Configuration decides whether an instrumentation can participate; it does not select an owner.

Processing ownership does not deduplicate `messaging.client.consumed.messages`. Multiple observers
can still count the same delivery. Delivery identity and cross-layer counting belong to
[issue #20214](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/20214),
not to process-span selection.

## Select at the handoff

Trace the actual route from client to application before suppressing either observer. An active
span, a framework on the classpath, or a thread-local flag covering an entire listener does not
show that both observers own the same work.

- A RabbitMQ consumer registration can retain whether a supported Spring Rabbit listener owns its
  callbacks. Other consumers on the same channel must remain independently instrumented.
- A Kafka poll can carry selection for the records or batch handed to Spring Kafka, Streams, or
  Connect. Reactor selects at downstream `onNext`; Vert.x selects at its record or batch handler.
  Do not suppress a whole callback merely because it contains a framework-owned delivery, or
  mistake internal standby polling for application processing.
- Pulsar and JMS listeners, Camel consumers, and Spring Integration handlers select at the
  supported delivery or invocation handoff. Separate handler invocations, including nested sends
  and retries, get their own lifecycle.
- For SQS, select a supported framework listener only for the concrete response or message handed
  to it. Otherwise SDK response traversal remains the observable processing path.

Keep the decision with the registration, batch, response, or invocation that needs it. If the
library copies or wraps a message, transfer the necessary parent and selection state to the new
carrier; matching by thread or object identity alone loses the handoff. Keep agent-only selection
state out of application message headers. If callbacks can overlap, tie selection to the invocation
rather than an unsynchronized shared flag. Do not add a global framework-present switch or a
dependency from a lower-level client module to an optional adapter.

## Finish the invocation that started

Capture the parent context at the receive or framework handoff, and retain the request and
operation context with the processing invocation. Start the process span according to the
instrumentation's semantic-convention policy. Make its context current while application code
runs, then close that thread's scope at the callback or method exit. Record any processing
duration and error and end the matching operation at the completion the library exposes.
Never reconstruct its parent from the ambient context on a completion thread.

Ordinary paired advice can use [AdviceScope](javaagent-advice-patterns.md#advicescope-patterns).
For asynchronous work, retain invocation state until the supported callback or future completes
on success, failure, or cancellation; do not carry an open thread scope across that wait.
For nested or reentrant callbacks, restore the previous invocation on exit and finish only the
invocation that callback started. Distinguish duplicate notifications for one invocation from a
new, reentrant handler invocation. Setup failures and failed receives must also clean up captured
state, including failures after a non-null message has been obtained.

## Suppress only the delegated work

Temporary suppression guards a known lower-level call; it is not the ownership decision.
With `ScopedThreadSuppression`, release suppression only if this invocation acquired it. Restore
any prior temporary thread state when leaving nested advice; see
[thread-local state](javaagent-thread-local-state.md). Do not let framework selection bypass
unrelated, existing suppression rules.

When a framework is disabled, absent, or cannot handle a particular listener, batch, or response,
the client instrumentation must still process the work it can observe. A nested application
client operation is new work, even when it runs inside a framework-owned callback. Test the
selected path, the unsupported or disabled fallback, and that nested client path separately.

## Stop at observable boundaries

Raw Kafka and AWS SDK v1/v2 SQS may have no application callback beyond traversal. Process only
items exposed by the supported iterator, list view, `forEach`, or spliterator path, and pair each
start with the completion that path actually observes. Accessing a list for bookkeeping is not
necessarily processing; list views, sublists, and split or parallel traversal must not invent
callbacks or rely on one thread's state for another thread's completion.

An abandoned iterator has no reliable completion event. Do not claim complete processing,
acknowledgment, or broker delivery from an incomplete traversal. Keep the limitation explicit
rather than synthesizing a lifecycle event.

## Review checklist

- Identify the exact registration, record, batch, message, or response handed between observers.
- Check that only one layer owns each observed process operation, including its parent, duration,
  error, and completion; keep other operations and consumed-message counting separate.
- Pair nested and asynchronous completions with the right invocation, and clean up on every
  supported failure path.
- Preserve independent nested client work and the lower-level fallback when selection cannot
  happen.
- Limit raw traversal claims to work and completion that the instrumented API actually exposes.
