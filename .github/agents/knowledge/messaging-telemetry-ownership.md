# [Messaging] Processing ownership

Use when a client and a framework can observe the same messaging processing work.

## Choose at the handoff

One layer owns each callback or observable traversal: its Process span, parent context, error,
duration when recorded, and completion. Select that layer from the actual registration or
delivery handoff, not an active span, thread, or framework on the classpath. Spring Rabbit can
make the choice per consumer registration; Spring Kafka can carry it with a polled batch and
its records.

Keep the choice with the registration, message, batch, or response that needs it, including
when a library copies or wraps the carrier. Suppress lower-level processing only for work the
framework owns. Independent nested client work stays visible, and the client remains the
fallback when the framework is absent, disabled, or cannot handle that callback. Configuration
enablement and temporary suppression are not ownership decisions.

## Finish the same invocation

Capture the parent at the processing handoff. Make the operation context current only while
application code runs; retain invocation state for nested or asynchronous callbacks so the
matching operation records its error and duration and ends at the observed completion. Restore
prior thread state and clean up on failures. Do not infer the parent from the context current
on a completion thread. Use [AdviceScope](javaagent-advice-patterns.md#advicescope-patterns)
for ordinary paired advice; release `ScopedThreadSuppression` only if this invocation acquired
it, following the [thread-state guidance](javaagent-thread-local-state.md).

## Keep the boundary honest

A retry, another handler, or a nested delivery may be a new Process operation. Raw Kafka and
SQS traversal is only best-effort processing: instrument supported iterator or callback
boundaries, not arbitrary list access. An abandoned iterator has no reliable completion event.

Receive, Send, Create, and propagation have separate lifecycles. Process ownership does not
deduplicate `messaging.client.consumed.messages`; that needs delivery identity, tracked in
[issue #20214](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/20214).
