# Using the `Instrumenter` API

The `Instrumenter` encapsulates the entire logic for gathering telemetry, from collecting the data,
to starting and ending spans, to recording values using metrics instruments. The `Instrumenter`
public API contains only three methods: `shouldStart()`, `start()` and `end()`. The class is
designed to decorate the actual invocation of the instrumented library code; `shouldStart()`
and `start()` methods are to be called at the start of the request processing, while `end()` must be
called when processing ends and a response arrives, or when it fails with an error.
The `Instrumenter` is a generic class parameterized with `REQUEST` and `RESPONSE` types. They
represent the input and output of the instrumented operation. `Instrumenter` can be configured with
various extractors that can enhance or modify the telemetry data.

## Check if any telemetry should be generated for the operation using `shouldStart()`

The first method, which needs to be called before any other `Instrumenter` method,
is `shouldStart()`. It determines whether the operation should be instrumented for telemetry or not.
The `Instrumenter` framework implements several suppression rules that prevent generating duplicate
telemetry; for example the same HTTP server request always produces a single HTTP `SERVER` span.

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

If the `shouldStart()` method returns `false`, none of the remaining `Instrumenter` methods should
be called.

## Start an instrumented operation using `start()`

When `shouldStart()` returns `true`, you can use `start()` to initiate the instrumented operation.
The `start()` method begins gathering telemetry about the instrumented library function that's being
invoked. It starts the `Span` and begins recording the metrics (if any are registered in the
used `Instrumenter` instance).

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
called.

## End an instrumented operation using `end()`

The `end()` method is called when the instrumented operation finished. It is of extreme importance
for this method to be always called after `start()`. Calling `start()` without later `end()`
will result in inaccurate or wrong telemetry and context leaks. The `end()` method ends the current
span and finishes recording the metrics (if any are registered in the `Instrumenter` instance).

The `end()` method accepts several arguments:

- The OpenTelemetry `Context` that was returned by the `start()` method.
- The `REQUEST` instance that started the processing.
- Optionally, the `RESPONSE` instance that ends the processing - it may be `null` in case it was not
  received or an error has occurred.
- Optionally, a `Throwable` error that was thrown by the operation.

Consider the following example:

```java
Response decoratedMethod(Request request) {
  Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
    return actualMethod(request);
  }

  Context context = instrumenter.start(parentContext, request);
  Response response;
  try (Scope scope = context.makeCurrent()) {
    response = actualMethod(request);
  } catch (Throwable t) {
    instrumenter.end(context, request, null, t);
    throw t;
  }
  // calling end after the scope is closed is a best practice
  instrumenter.end(context, request, response, null);
  return response;
}
```

In the code sample the `context` returned by the `start()` method is passed along to the `end()`
method. The `end()` method is always called, regardless of the outcome of the
decorated `actualMethod()`, be it a valid response or an error.

## Constructing a new `Instrumenter` using an `InstrumenterBuilder`

An `Instrumenter` can be obtained by calling its static `builder()` method and using the
returned `InstrumenterBuilder` to configure captured telemetry and apply customizations.
The `builder()` method accepts three arguments:

- An `OpenTelemetry` instance, which is used to obtain the `Tracer` and `Meter` objects.
- The instrumentation name, which indicates the _instrumentation_ library name, not the
  _instrumented_ library name. The value passed here should uniquely identify the instrumentation
  library so that during troubleshooting it's possible to determine where the telemetry came from.
  Read more about instrumentation libraries in
  the [OpenTelemetry specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#instrumentation-libraries).
- A `SpanNameExtractor` that determines the span name.

An `Instrumenter` can be built from several smaller components. The following subsections describe
all interfaces that can be used to customize an `Instrumenter`.

### Set the instrumentation version and OpenTelemetry schema URL

By setting the instrumentation library version, you let users identify which version of your
instrumentation produced the telemetry. Make sure you always provide the version to
the `Instrumenter`. You can do this in two ways:

- By calling the `setInstrumentationVersion()` method on the `InstrumenterBuilder`.
- By making sure that the JAR file with your instrumentation library contains a properties file in
  the `META-INF/io/opentelemetry/instrumentation/` directory. You must name the file
  `${instrumentationName}.properties`, where `${instrumentationName}` is the name of the
  instrumentation library passed to the `Instrumenter#builder()` method. The file must contain a
  single property, `version`. For example:

  ```properties
  # META-INF/io/opentelemetry/instrumentation/my-instrumentation.properties
  version=1.2.3
  ```

  The `Instrumenter` automatically detects the properties file and determines the instrumentation
  version based on its name.

If the `Instrumenter` adheres to a specific OpenTelemetry schema, you can set the schema URL using
the `setSchemaUrl()` method on the `InstrumenterBuilder`. To learn more about the OpenTelemetry
schemas [see the Overview](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/schemas/README.md).

### Name the spans using the `SpanNameExtractor`

A `SpanNameExtractor` is a simple functional interface that accepts the `REQUEST` type and returns
the span name. For more detailed guidelines on span naming please take a look at
the [`Span` specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span)
and the
tracing [semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/trace.md).

Consider the following example:

```java
class MySpanNameExtractor implements SpanNameExtractor<Request> {

  @Override
  public String extract(Request request) {
    return request.getOperationName();
  }
}
```

The example `SpanNameExtractor` implementation takes a fitting value provided by the request type
and uses it as a span name. Notice that `SpanNameExtractor` is a `@FunctionalInterface`: instead of
implementing it as a separate class you can just pass `Request::getOperationName` to the `builder()`
method.

### Add attributes to span and metric data with the `AttributesExtractor`

An `AttributesExtractor` is responsible for extracting span and metric attributes when the
processing starts and ends. It contains two methods:

- The `onStart()` method is called when the instrumented operation starts. It accepts two
  parameters: an `AttributesBuilder` instance and the incoming `REQUEST` instance.
- The `onEnd()` method is called when the instrumented operation ends. It accepts the same two
  parameters as `onStart()` and also an optional `RESPONSE` and an optional `Throwable` error.

The aim of both methods is to extract interesting attributes from the received request (and response
or error) and set them into the builder. In general, it is better to populate attributes
`onStart()`, as these attributes will be available to the `Sampler`.

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
request, one from the response. It is recommended to keep `AttributeKey` instances as static final
constants and reuse them. Creating a new key each time an attribute is set risks introducing
unnecessary overhead.

You can add an `AttributesExtractor` to the `InstrumenterBuilder` by using
the `addAttributesExtractor()` or `addAttributesExtractors()` methods.

### Set span status by setting the `SpanStatusExtractor`

By default, the span status is set to `StatusCode.ERROR` when a `Throwable` error occurs, and
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
      return response.isSuccessful() ? StatusCode.OK : StatusCode.ERROR;
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

- A `SpanLinkBuilder` that can be used to add the links.
- The parent `Context` that was passed in to `Instrumenter#start()`.
- The `REQUEST` instance that was passed in to `Instrumenter#start()`.

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

In the `SpanLinksExtractor` sample implementation, the request object uses a
convenient `getRelatedOperations()` method to find all spans that should be linked to the newly
created one. When such a function doesn't exist, you need to construct `SpanContext` by extracting
information from a data structure representing request headers or metadata.

You can add a `SpanLinksExtractor` to the `InstrumenterBuilder` by using
the `addSpanLinksExtractor()` method.

### Discard wrapper exception types with the `ErrorCauseExtractor`

When an error occurs, the root cause might be hidden behind several "wrapper" exception types, like
an `ExecutionException` or a `CompletionException` from the Java standard library. By default, the
known wrapper exception types from the JDK are removed from the captured error. To remove other
wrapper exceptions, like the ones provided by the instrumented library, you can implement
the `ErrorCauseExtractor`, which has the following features:

- It has only one method `extractCause()` that is responsible for stripping the unnecessary
  exception layers and extracting the actual error that caused the operation to fail.
- It accepts a `Throwable` and returns a `Throwable`.

Consider the following example:

```java
class MyErrorCauseExtractor implements ErrorCauseExtractor {

  @Override
  public Throwable extract(Throwable error) {
    if (error instanceof MyLibWrapperException && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.jdk().extractCause(error);
  }
}
```

The example `ErrorCauseExtractor` implementation checks whether the error is an instance
of `MyLibWrapperException` and has a cause, in which case it unwraps it.
The `error.getCause() != null` check is very relevant: if the extractor did not verify both
conditions, it could accidentally remove the whole exception, making the instrumentation miss an
error and thus radically changing the captured telemetry. Next, the extractor falls back to the
default `jdk()` implementation that removes the known JDK wrapper exception types.

You can set the `ErrorCauseExtractor` in the `InstrumenterBuilder` using
the `setErrorCauseExtractor()` method.

### Register metrics by implementing the `OperationMetrics` and `OperationListener`

If you need to add metrics to the `Instrumenter` you can implement the `OperationMetrics`
and `OperationListener` interfaces. `OperationMetrics` is simply a factory interface for
the `OperationListener` - it receives an OpenTelemetry `Meter` and returns a new listener.
The `OperationListener` contains two methods:

- `onStart()` that gets executed when the instrumented operation starts. It returns a `Context` - it
  can be used to store internal metrics state that should be propagated to the `onEnd()` call, if
  needed.
- `onEnd()` that gets executed when the instrumented operation ends.

Both methods accept a `Context`, an instance of `Attributes` that contains either attributes
computed on instrumented operation start or end, and the start and end nanoseconds timestamp that
can be used to accurately compute the duration.

Consider the following example:

```java
class MyOperationMetrics implements OperationListener {

  static OperationMetrics get() {
    return MyOperationMetrics::new;
  }

  private final LongUpDownCounter activeRequests;

  MyOperationMetrics(Meter meter) {
    activeRequests = meter
        .upDownCounterBuilder("mylib.active_requests")
        .setUnit("{requests}")
        .build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, startAttributes);
    return context.with(new MyMetricsState(startAttributes));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    MyMetricsState state = MyMetricsState.get(context);
    activeRequests.add(1, state.startAttributes());
  }
}
```

The sample class listed above implements the `OperationMetrics` factory interface in the
static `get()` method. The listener implementation uses a counter to measure the number of requests
that are currently in flight. Notice that the state between `onStart()` and `onEnd()` method is
shared using the `MyMetricsState` class (a mostly trivial data class, not listed in the example
above), passed between the methods using the `Context`.

You can add `OperationMetrics` to the `InstrumenterBuilder` using the `addOperationMetrics()` method.

### Enrich the operation `Context` with the `ContextCustomizer`

In some rare cases, there is a need to enrich the `Context` before it is returned from
the `Instrumenter#start()` method. The `ContextCustomizer` interface can be used to achieve that. It
exposes a single `onStart()` method that accepts a `Context`, a `REQUEST` and `Attributes` extracted
on the operation start, and returns a modified `Context`.

Consider the following example:

```java
class MyContextCustomizer implements ContextCustomizer<Request> {

  @Override
  public Context onStart(Context context, Request request, Attributes startAttributes) {
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

In some rare cases it may be useful to completely disable the constructed `Instrumenter`, for
example, based on a configuration property. The `InstrumenterBuilder` exposes a `setEnabled()`
method for that: passing `false` will turn the newly created `Instrumenter` into a no-op instance.

### Finally, set the span kind with the `SpanKindExtractor` and get a new `Instrumenter`

The `Instrumenter` creation process ends with calling one of the following `InstrumenterBuilder`
methods:

- `buildInstrumenter()`: the returned `Instrumenter` will always start spans with kind `INTERNAL`.
- `buildInstrumenter(SpanKindExtractor)`: the returned `Instrumenter` will always start spans with
  kind determined by the passed `SpanKindExtractor`.
- `buildClientInstrumenter(TextMapSetter)`: the returned `Instrumenter` will always start `CLIENT`
  spans and will propagate operation context into the outgoing request.
- `buildServerInstrumenter(TextMapGetter)`: the returned `Instrumenter` will always start `SERVER`
  spans and will extract the parent span context from the incoming request.
- `buildProducerInstrumenter(TextMapSetter)`: the returned `Instrumenter` will always start `PRODUCER`
  spans and will propagate operation context into the outgoing request.
- `buildConsumerInstrumenter(TextMapGetter)`: the returned `Instrumenter` will always start `CONSUMER`
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
the [OpenTelemetry messaging semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-spans.md#span-kind)
the span kind should be set to `CLIENT` if sending the message is completely synchronous and waits
for the response.
