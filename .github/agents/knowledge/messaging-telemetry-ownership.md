# [Messaging] Processing ownership

Use when a client and a framework can observe the same messaging processing work.

When two layers see the *same work*, only one should produce its Process span and record its
parent, error, duration, and completion. The unit of work is a callback or observable traversal,
not a message's entire lifetime. Another handler, a retry, or a nested delivery may be new work
with its own Process span.

## Decide where the work changes hands

Choose the owner at the actual client-to-framework handoff. A framework on the classpath, the
current span, or a shared thread does not tell you which layer owns a particular callback.
Configuration enables an instrumentation; temporary suppression prevents a duplicate span
during known delegation. Neither makes the ownership decision.

For example:

- **Spring Rabbit:** The client and Spring both see deliveries to a Spring-created consumer.
  Mark that consumer when it registers. Spring then emits the Process span when it invokes the
  listener. A separately registered RabbitMQ consumer, even on the same channel, still gets a
  client Process span.
- **Spring Kafka:** A poll returns records that might be iterated later or on another thread.
  Spring marks the batch and its records for listener processing, so later iteration does not
  create a second client Process span. Raw Kafka iteration still instruments independent
  application polls.
- **SQS:** An SDK receive call returns messages without a processing callback. Iteration is
  the best boundary available to a raw SDK user. If a supported framework handles that
  response, it owns the messages it passes to its listener; otherwise SDK traversal is the
  fallback.

Keep ownership information with the registration, batch, or message that crosses the handoff.
Transfer it when the library copies or wraps a message. If the framework is absent, disabled,
or cannot handle the callback, leave client processing available. Do not disable all client
processing just because one framework delivery is in progress.

## Name the handoff in code

When the caller knows the owner, name it: `markSpringKafkaAsProcessingOwner(records)` or
`markSpringRabbitAsProcessingOwner(consumer)`. Shared client code may only know that processing
belongs elsewhere. Name that side `markProcessingOwnedOutsideKafkaClient(records)`, with state
such as `processingOwnedOutsideKafkaClient`. This is the same handoff from two viewpoints: Spring
Kafka knows who will process the batch; Kafka clients only know not to emit a raw Process span for
those records. Use `mark`, not `claim`, for a marker write that does not arbitrate ownership.

Keep other roles distinct. `canTraceListenerProcessing` checks whether a listener can be
instrumented; `rawProcessingEligibility` can combine ownership and configuration;
`ProcessingInvocation` or `MessageInvocation` tracks a single attempt through completion.
`listenerProcessingScope` is temporary callback state, while `LISTENER_MODE` stores
configuration. Neither is an owner marker. Use `current*` for temporary thread state and
`*Enabled` for configuration, not ownership. These are naming patterns, not a shared API.
Ordinary paired advice need not add an invocation object.

## Finish the work you started

Resolve the parent context when each callback or supported traversal invocation begins, then make
the Process context current while application code runs. Retain that exact parent and enough state
to finish the invocation, including across nested callbacks or asynchronous completion. Close the
thread scope when the callback returns. At the completion you can observe, record its error and
duration and end the operation. Restore previous thread state on exit, including failure. Do not
look up the parent from the context current on the completion thread.

Ordinary paired advice can use [AdviceScope](javaagent-advice-patterns.md#advicescope-patterns).
When suppressing lower-level processing with `ScopedThreadSuppression`, release it only if
this invocation acquired it; see [thread-state guidance](javaagent-thread-local-state.md).

Raw Kafka and SQS traversal is best effort. Instrument the supported iterator or callback
boundary, not arbitrary list access. An abandoned iterator has no reliable completion event.

Receive, Send, Create, and context propagation have their own boundaries. Process ownership
does not deduplicate `messaging.client.consumed.messages`; that requires delivery identity,
tracked in [issue #20214](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/20214).
