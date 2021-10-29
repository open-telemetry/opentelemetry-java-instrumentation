# Using the `Instrumenter` API

The `Instrumenter` encapsulates the entire logic of gathering telemetry: collecting the data,
starting and ending spans, recording values using metrics instruments. The `Instrumenter` public API
contains only three methods: `shouldStart()`, `start()` and `end()`. The class is designed to
decorate the actual invocation of the instrumented library code; `shouldStart()` and `start()`
methods are to be called at the start of the request processing, `end()` must be called when
processing ends and a response arrives, or when it fails with an error. The `Instrumenter` is a
generic class parameterized with `REQUEST` and `RESPONSE` types. They represent the input and output
of the instrumented operation. `Instrumenter` can be configured with various extractors that can
enhance or modify the telemetry data.

## Check if any telemetry should be generated for the operation using `shouldStart()`

The first method, one that needs to be called before any other `Instrumenter` method,
is `shouldStart()`. It determines whether the operation should be instrumented for telemetry or not.
The `Instrumenter` framework implements several suppression rules that prevent generating duplicate
telemetry; for example the same HTTP server request will always emit exactly one HTTP `SERVER` span,
not more.

The `shouldStart()` method accepts the current OpenTelemetry `Context` and the instrumented
library `REQUEST` type. Consider the following example:

```java
Response decoratedMethod(Request request) {
  Context parentContext = Context.current();
  if (!instrumenter.shouldStart(parentContext, request)) {
    return actualMethod(request);
  }

  // ...
}
```

If the `shouldStart()` method returns `false`, none of the remaining `Instrumenter` methods are
called.

## Start an instrumented operation using `start()`

When `shouldStart()` returns `true` it is time to `start()` the instrumented operation.
The `start()` method begins gathering telemetry about the instrumented library function that's being
invoked. It starts the `Span` and begins recording the metrics (if any are registered in
the used `Instrumenter` instance).

The `start()` method accepts the current OpenTelemetry `Context` and the instrumented
library `REQUEST` type, and returns the new OpenTelemetry `Context` that should be made current
until the instrumented operation ends. Consider the following example:

```java
Response decoratedMethod(Request request) {
  // ...

  Context context = instrumenter.start(parentContext, request);
  try (Scope scope = context.makeCurrent()) {
    // ...
  }
}
```

The newly started `context` is made current, and inside its `scope` the actual library method is
called. After that the instrumented operation ends.

## End an instrumented operation using `end()`

The `end()` method is called when the instrumented operation finished. It is of extreme importance
for this method to be always called after `start()`. Calling `start()` without later `end()`
may result in inaccurate or wrong telemetry and context leaks. The `end()` method ends the current
span and finishes recording the metrics (if any are registered in the `Instrumenter` instance).

The method accepts several arguments:

* The current OpenTelemetry `Context` - that is, the one that was returned by the `start()` method.
* The `REQUEST` instance that has started the processing.
* Optionally, the `RESPONSE` instance that ends the processing - it may be `null` in case it was not
  received or an error has occurred.
* Optionally, a `Throwable` error that was thrown during the processing.

Consider the following example:

```java
Response decoratedMethod(Request request) {
  // ...

  Context context = instrumenter.start(parentContext, request);
  try (Scope scope = context.makeCurrent()) {
    Response response = actualMethod(request);
    instrumenter.end(context, request, response, null);
    return response;
  } catch (Throwable error) {
    instrumenter.end(context, request, null, error);
    throw error;
  }
}
```

In the code sample the `context` returned by the `start()` method is passed along to the `end()`
method. The `end()` method is always called, regardless of the outcome of the
decorated `actualMethod()` - be it a valid response or an error.

## Constructing a new `Instrumenter` using an `InstrumenterBuilder`

An `Instrumenter` can be obtained by calling its static `builder()` method and using the
returned `InstrumenterBuilder` to configure captured telemetry and apply customizations to the new
instance. The `builder()` method accepts three arguments:

* An `OpenTelemetry` instance, which will be used to obtain the `Tracer` and `Meter` objects.
* The instrumentation name - it denotes the _instrumentation_ library name, not the _instrumented_
  library name. The value passed here should uniquely identify the instrumentation library so that
  during troubleshooting it's possible to determine where the telemetry came from.
* A `SpanNameExtractor` that determines the span name.

An `Instrumenter` can be built from several smaller components. The following sub-sections describe
all interfaces that can be used to customize an `Instrumenter`.

### Name the spans using the `SpanNameExtractor`

A `SpanNameExtractor` is a simple function interface that accepts the `REQUEST` type and returns the
span name. For more detailed guidelines on span naming please take a look at
the [`Span` specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span)
and the
tracing [semantic conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/README.md).

Consider the following example:

```java
class MySpanNameExtractor implements SpanNameExtractor<Request> {

  @Override
  public String extract(Request request) {
    return request.getOperationName();
  }
}
```

The example `SpanNameExtractor` implementation simply takes a fitting value provided by the request
type and uses it as a span name. It is worth noting that `SpanNameExtractor` is
a `@FunctionalInterface` and instead of implementing it as a separate class you could just
pass `Request::getOperationName` to the `builder()` method.

### Add attributes to span and metric data with the `AttributesExtractor`

An `AttributesExtractor` is responsible for extracting span and metric attributes when the
processing starts and ends. It contains two methods:

* The `onStart()` method is called when the instrumented operation starts. It accepts two
  parameters: an `AttributesBuilder` instance and the incoming `REQUEST` instance.
* The `onEnd()` method is called when the instrumented operation ends. It accepts the same two
  parameters as `onStart()` and also an optional `RESPONSE` and an optional `Throwable` error.

The aim of both methods is to extract interesting attributes from the received request (and
response or error) and set them into the builder. In general, it is better to populate
attributes `onStart()`, as these attributes will be available to the `Sampler`.

Consider the following example:

```java
class MyAttributesExtractor implements AttributesExtractor<Request, Response> {

  private static final AttributeKey<String> NAME = stringKey("mylib.name");
  private static final AttributeKey<Long> COUNT = longKey("mylib.count");

  @Override
  public void onStart(AttributesBuilder attributes, Request request) {
    set(attributes, NAME, request.getName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Request request,
      @Nullable Response response,
      @Nullable Throwable error) {
    if (response != null) {
      set(attributes, COUNT, response.getCount());
    }
  }
}
```

The sample `AttributesExtractor` implementation above sets two attributes: one extracted from the
request, one from the response. In general, it is recommended to keep `AttributeKey` instances as
static final constants and reuse them. Creating a new key each time an attribute is set will
introduce unnecessary overhead.

You can add an `AttributesExtractor` to the `InstrumenterBuilder` by using
the `addAttributesExtractor()` or `addAttributesExtractors()` methods.

### Set span status by setting the `SpanStatusExtractor`

By default, the span status is set to `StatusCode.ERROR` when a `Throwable` error occurs, or
to `StatusCode.UNSET` in all other cases. Setting a custom `SpanStatusExtractor` allows to customize
this behavior.

The `SpanStatusExtractor` interface has only one method `extract()` that accepts the `REQUEST`, an
optional `RESPONSE` and an optional `Throwable` error. It is supposed to return a `StatusCode` that
will be set when the span wrapping the instrumented operation ends. Consider the following example:

```java
class MySpanStatusExtractor implements SpanStatusExtractor<Request, Response> {

  @Override
  public StatusCode extract(
      Request request,
      @Nullable Response response,
      @Nullable Throwable error) {
    if (response != null) {
      return response.isSuccessful() ? StatusCode.SUCCESS : StatusCode.ERROR;
    }
    return SpanStatusExtractor.getDefault().extract(request, response, error);
  }
}
```

The sample `SpanStatusExtractor` implementation above returns a custom `StatusCode` depending on the
operation result, encoded in the response class. If the response was not present (for example,
because of an error) it falls back to the default behavior, represented by
the `SpanStatusExtractor.getDefault()` method.

You can set the `SpanStatusExtractor` in the `InstrumenterBuilder` by using
the `setSpanStatusExtractor()` method.

### Add span links using the `SpanLinksExtractor`

The `SpanLinksExtractor` interface can be used to add links to other spans when the instrumented
operation starts. It has a single `extract()` method that receives the following arguments:

* A `SpanLinkBuilder` that can be used to add the links.
* The parent operation `Context` from before the instrumented operation has started.
* The `REQUEST` instance that has started the processing.

You can read more about span links and their use
cases [here](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#links-between-spans).

Consider the following example:

```java
class MySpanLinksExtractor implements SpanLinksExtractor<Request> {

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, Request request) {
    for (RelatedOperation op : request.getRelatedOperations()) {
      spanLinks.addLink(op.getSpanContext());
    }
  }
}
```

In the sample `SpanLinksExtractor` implementation the request object uses a very
convenient `getRelatedOperations()` method to find all spans that should be linked to the newly
created one. In reality, you will most likely have to construct `SpanContext` by extracting
information from a data structure representing request headers or metadata.

You can add a `SpanLinksExtractor` to the `InstrumenterBuilder` by using
the `addSpanLinksExtractor()` method.

### Discard wrapper exception types with the `ErrorCauseExtractor`

Sometimes when an error occurs, the real cause is hidden behind several "wrapper" exception types -
for example, an `ExecutionException` or a `CompletionException` from the Java standard library. By
default, the known wrapper exception types from the JDK are removed from the captured error. To
remove other wrapper exceptions, like the ones provided by the instrumented library, you can
implement the `ErrorCauseExtractor`. It has only one method `extractCause()` that is responsible for
stripping the unnecessary exception layers and extracting the actual error that caused the
processing to fail. It accepts a `Throwable` and returns a `Throwable`.

Consider the following example:

```java
class MyErrorCauseExtractor implements ErrorCauseExtractor {

  @Override
  public Throwable extractCause(Throwable error) {
    if (error instanceof MyLibWrapperException && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.jdk().extractCause(error);
  }
}
```

The example `ErrorCauseExtractor` implementation checks whether the error is an instance
of `MyLibWrapperException` and has a cause - if so, it unwraps it. The `error.getCause() != null`
check is rather important here: if the extractor did not verify this it could accidentally remove
the whole exception, making the instrumentation miss an error and thus radically changing the
captured telemetry. Next, the extractor falls back to the default `jdk()` implementation that
removes the known JDK wrapper exception types.

You can set the `ErrorCauseExtractor` in the `InstrumenterBuilder` using
the `setErrorCauseExtractor()` method.

### Provide custom operation start and end times using the `StartTimeExtractor` and `EndTimeExtractor`

In some cases, the instrumented library provides a way to retrieve accurate timestamps of when the
operation starts and ends. The `StartTimeExtractor` and `EndTimeExtractor` interfaces can be used to
feed this information into OpenTelemetry trace and metrics data. Ether both time extractors or none
at all need to be provided to the `InstrumenterBuilder`. It is crucial to avoid a situation where
one time measurement uses the library timestamp and the other the internal OpenTelemetry SDK clock,
as it would result in inaccurate telemetry.

The `StartTimeExtractor` can only extract the timestamp from the request. The `EndTimeExtractor`
accepts the request, an optional response, and an optional `Throwable` error. Consider the following
example:

```java
class MyStartTimeExtractor implements StartTimeExtractor<Request> {

  @Override
  public Instant extract(Request request) {
    return request.startTimestamp();
  }
}

class MyEndTimeExtractor implements EndTimeExtractor<Request, Response> {

  @Override
  public Instant extract(Request request, @Nullable Response response, @Nullable Throwable error) {
    if (response != null) {
      return response.endTimestamp();
    }
    return request.clock().now();
  }
}
```

The sample implementations above use the request to retrieve the start timestamp. The response is
used to compute the end time if it is available; in case it is missing (for example, when an error
occurs) the same time source is used to compute the current timestamp.

You can set both time extractors in the `InstrumenterBuilder` using the `setTimeExtractors()`
method.

### Register metrics by implementing the `RequestMetrics` and `RequestListener`

If you need to add metrics to the `Instrumenter` you can implement the `RequestMetrics`
and `RequestListener` interfaces. `RequestMetrics` is simply a factory interface for
the `RequestListener` - it receives an OpenTelemetry `Meter` and returns a new listener.
The `RequestListener` contains two methods:

* `start()` that gets executed when the instrumented operation starts. It returns a `Context` - it
  can be used to store internal metrics state that should be propagated to the `end()` call, if
  needed.
* `end()` that gets executed when the instrumented operation ends.

Both methods accept a `Context`, an instance of `Attributes` that contains either attributes
computed on instrumented operation start or end, and the start/end nanoseconds timestamp that can be
used to accurately compute the duration.

Consider the following example:

```java
class MyRequestMetrics implements RequestListener {

  static RequestMetrics get() {
    return MyRequestMetrics::new;
  }

  private final LongUpDownCounter activeRequests;

  MyRequestMetrics(Meter meter) {
    activeRequests = meter
        .upDownCounterBuilder("mylib.active_requests")
        .setUnit("requests")
        .build();
  }

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, startAttributes);
    return context.with(new MyMetricsState(startAttributes));
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    MyMetricsState state = MyMetricsState.get(context);
    activeRequests.add(1, state.startAttributes());
  }
}
```

The sample class listed above implements the `RequestMetrics` factory interface in the
static `get()` method. The listener implementation uses a counter to measure the number of requests
that are currently in flight. Notice that the state between `start()` and `end()` method is shared
using the `MyMetricsState` class (a mostly trivial data class, not listed in the example above),
passed between the methods using the `Context`.

You can add `RequestMetrics` to the `InstrumenterBuilder` using the `addRequestMetrics()` method.

### Enrich the operation `Context` with the `ContextCustomizer`

In some rare cases, there is a need to enrich the `Context` before it is returned from
the `Instrumenter#start()` method. The `ContextCustomizer` interface can be used to achieve that. It
exposes a single `start()` method that accepts a `Context`, a `REQUEST` and `Attributes` extracted
on the operation start, and returns a modified `Context`.

Consider the following example:

```java
class MyContextCustomizer implements ContextCustomizer<Request> {

  @Override
  public Context start(Context context, Request request, Attributes startAttributes) {
    return context.with(new InProcessingAttributesHolder());
  }
}
```

The sample `ContextCustomizer` listed above inserts an additional `InProcessingAttributesHolder` to
the `Context` before it is returned from the `Instrumenter#start()` method.
The `InProcessingAttributesHolder` class, as its name implies, may be used to keep track of
attributes that are not available on request start or end - for example, if the instrumented library
exposes important information only during the processing. The holder class can be looked up from the
current `Context` and filled in with that information between the instrumented operation start and
end. It can be later passed as `RESPONSE` type (or a part of it) to the `Instrumenter#end()` method
so that the configured `AttributesExtractor` can process the collected information and turn it into
telemetry attributes.

You can add a `ContextCustomizer` to the `InstrumenterBuilder` using the `addContextCustomizer()`
method.

### Disable the instrumentation

In some rare cases it may be useful to completely disable the constructed `Instrumenter`, e.g. based
on a configuration property. The `InstrumenterBuilder` exposes a `setDisabled()` method for that -
passing `true` will turn the newly created `Instrumenter` into an effectively no-op instance.

### Set the span kind with the `SpanKindExtractor` and get a new `Instrumenter`

The `Instrumenter` creation process ends with calling one of the following `InstrumenterBuilder`
methods:

* `newInstrumenter()` - the returned `Instrumenter` will always start spans with kind `INTERNAL`.
* `newInstrumenter(SpanKindExtractor)` - the returned `Instrumenter` will always start spans with
  kind determined by the passed `SpanKindExtractor`.
* `newClientInstrumenter(TextMapSetter)` - the returned `Instrumenter` will always start `CLIENT`
  spans and will propagate current context into the outgoing request.
* `newServerInstrumenter(TextMapGetter)` - the returned `Instrumenter` will always start `SERVER`
  spans and will extract the parent span context from the incoming request.
* `newProducerInstrumenter(TextMapSetter)` - the returned `Instrumenter` will always
  start `PRODUCER` spans and will propagate current context into the outgoing request.
* `newConsumerInstrumenter(TextMapGetter)` - the returned `Instrumenter` will always start `SERVER`
  spans and will extract the parent span context from the incoming request.

The last four variants that create non-`INTERNAL` spans accept either `TextMapSetter`
or `TextMapGetter` implementations as parameters. These are needed to correctly implement the
context propagation between services. If you want to learn how to use and implement these
interfaces, read
the [OpenTelemetry Java docs](https://opentelemetry.io/docs/java/manual_instrumentation/#context-propagation).

The `SpanKindExtractor` interface, accepted by the second variant from the list above, is a simple
interface that accepts a `REQUEST` and returns a `SpanKind` that should be used when starting the
span for this operation. Consider the following example:

```java
class MySpanKindExtractor implements SpanKindExtractor<Request> {

  @Override
  public SpanKind extract(Request request) {
    return request.shouldSynchronouslyWaitForResponse() ? SpanKind.CLIENT : SpanKind.PRODUCER;
  }
}
```

The example `SpanKindExtractor` above decides whether to use `PRODUCER` or `CLIENT` based on how the
request is going to be processed. This example reflects a real-life scenario: you might find
similar code in a messaging library instrumentation, since according to
the [OpenTelemetry messaging semantic conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#span-kind)
the span kind should be set to `CLIENT` if sending the message is completely synchronous and waits
for the response.
