# [Messaging] Processing ownership

Use when overlapping messaging observers can instrument the same processing work, or when a
framework delegates that work to a lower-level client instrumentation.

Processing ownership answers one question: which observer instruments a concrete callback or
observable traversal. For that work, one owner controls the process span, parent context, duration,
error reporting, and completion. Receive and send telemetry, propagation, and metrics have their
own boundaries and are not assigned by this contract.

Cross-layer consumed-message deduplication is outside this contract. Until the focused
[follow-up issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues?q=is%3Aissue%20%22Count%20consumed%20messages%20once%20across%20overlapping%20messaging%20instrumentations%22)
is created, this link intentionally points to a title search. Replace the search URL with the issue
URL after creation.

## Roles and names

Use these names for new or changed internal code. They describe roles, not shared APIs to add.

| Role | Meaning | Naming examples |
| --- | --- | --- |
| Processing selection | Which observer should instrument the concrete work | `processingSelected`, `isProcessingSelected()`, `processingOwner`, `processingSelection`, `ProcessingSelection` |
| Processing invocation | One attempt and its captured lifecycle state | `invocation`, `ProcessingInvocation`, `completed`; ordinary `AdviceScope.start()` / `end()` |
| Current thread state | State installed for the current thread | `currentInvocation`, `currentRequest`, `currentContext`, `currentScope`; `previous` for restoration |
| Configuration or eligibility | Whether instrumentation may run before selecting an owner | `processingEnabled`, `isProcessingEnabled()` |

Reserve `selected` and `owner` for responsibility, `enabled` for configuration or eligibility, and
`current` for ambient thread state. A name such as `isListenerProcessingSelected()` says more than a
framework marker such as `SPRING_CONSUMER`. If the selection applies to both batches and individual
records, use a role name such as `ProcessingSelection`, not `BatchState`.

Choose fields and types that fit the library lifecycle. Do not add a shared holder, enum, hierarchy,
interface, allocation, or wrapper only to enforce these names.

## Select from the concrete handoff

Prove that two observers see the same work through the actual registration, callback handoff, or
delegation path. A current span, shared thread, matching operation type, or framework class on the
classpath does not prove shared ownership.

For example, a consumer registration can select framework listener processing and retain that
decision in an immutable client wrapper flag. Scope the selection to that registration. An
unrelated client call made inside the listener must remain independently observable.

Keep global configuration enablement separate from ownership selection. If the selected framework
instrumentation is absent, disabled, or cannot support the concrete callback, leave the lower-level
client instrumentation available as the fallback.

Temporary suppression is a scoped gate around known delegation, not persistent ownership. Keep
acquisition, restoration, and cleanup visible. Release only what the invocation acquired, and
follow the [temporary thread-state guidance](javaagent-thread-local-state.md).

## Keep one process lifecycle together

The selected owner controls the whole process operation:

- capture the parent context at the library's processing boundary;
- start the process span from that parent according to the instrumentation's semantic-convention
  policy;
- make the returned operation context current only while the application work runs;
- measure duration and report errors for that same invocation;
- end the captured invocation at its observable completion boundary.

Do not let one layer start the span while another layer owns duration or completion. Split
responsibility only when the library exposes separate lifecycle boundaries and the instrumentation
documents how they pair.

Keep the parent context with the invocation state when processing can move to another thread or
finish asynchronously. Do not reconstruct the parent from whatever context happens to be current
at completion.

Ordinary paired advice should use the established
[AdviceScope patterns](javaagent-advice-patterns.md#advicescope-patterns). Async APIs should finish
on success, failure, or cancellation at the callback or future boundary they can observe. Setup
failure must restore thread state and leave no open scope or unfinished process operation.

## Match nested callbacks to their completion

Callbacks can nest, recur, or complete out of order. Store enough invocation state to finish the
exact callback that started it. A callback frame may contain the request, operation context, scope,
error, prior thread state, and completion flag.

Restore the previous frame or thread-local value after the callback. Do not remove an outer
invocation when an inner callback exits. Use a stack only when the API can nest callbacks; ordinary
paired advice does not need callback-frame machinery.

Known delegation of the same work should retain one owner through the nested call. Distinct work
started inside a callback gets its own selection and process lifecycle.

## Keep fallback behavior local

An adapter should suppress lower-level processing only for the concrete work it owns. Avoid global
framework-present flags and ambient markers that hide unrelated client processing.

Carry selection across the smallest handoff that reaches the processing callback. If the carrier
crosses class-loader boundaries, keep the shared value type visible to every participating module
and follow the existing [module guidance](javaagent-module-patterns.md). Do not make a lower-level
module depend on an optional framework module to preserve its fallback behavior.

Test both paths: the framework owner when supported, and the lower-level owner when the framework
instrumentation is disabled or the handoff is unsupported.

## Respect best-effort traversal boundaries

Raw Kafka and SQS iteration can be the only observable processing boundary when no framework
callback exists. Keep that processing as a best-effort fallback, but describe what the
instrumentation can actually observe.

- Start processing when an item is exposed through the supported iterator, `forEach`, spliterator,
  or list-view path.
- Finish at the corresponding iterator or callback boundary.
- Keep traversal state scoped to the concrete response, batch, or view that selected it.
- Preserve nested unrelated client work and framework handoff selection.
- Do not infer a completion event when traversal is abandoned. Iterator abandonment has no reliable
  completion boundary.

Do not add operations or claim full batch, retry, or broker coverage merely for consistency. An
unobservable boundary is a limitation to document, not a reason to invent lifecycle state.

## Recognizable shapes

These examples show comparable roles, not implementations to copy.

| Shape | Selection | Invocation and completion |
| --- | --- | --- |
| RabbitMQ client with a Spring Rabbit listener | Select from the concrete consumer registration and listener capability; retain the choice in that client wrapper | Capture the listener request, parent context, operation context, and scope in paired advice; finish that invocation |
| Kafka poll with Spring, Reactor, Vert.x, Streams, or Connect callbacks | Carry selection from the concrete batch or record handoff; keep raw iteration as the fallback | Callback state may need nested frames, previous thread state, errors, and async completion |
| SQS response with a supported framework listener | Select the framework only for the messages handed to its callback; keep raw response traversal as the fallback | Finish supported listener callbacks or futures directly; finish raw traversal only at observable iterator or callback boundaries |

The library lifecycle decides the storage and pairing mechanism. Keep the ownership contract the
same without forcing RabbitMQ, Kafka, and SQS into one state model.

## Review checklist

- The actual registration, handoff, or delegation path proves the selection.
- One observer owns the process span, parent context, duration, error, and completion.
- Nested callbacks finish their own captured invocation and restore previous thread state.
- Setup failure, exceptional completion, and cancellation clean up the owned state.
- Disabled or unsupported framework instrumentation leaves a working lower-level fallback.
- Kafka and SQS traversal claims stop at boundaries the instrumentation can observe.
